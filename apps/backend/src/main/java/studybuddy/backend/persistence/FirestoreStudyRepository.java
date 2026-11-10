package studybuddy.backend.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import studybuddy.backend.common.DomainException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Optional shared storage in Cloud Firestore, enabled with {@code APP_STORAGE=firestore}.
 *
 * <p>Layout: one collection per kind (for example {@code StudentProfile}), one document per
 * record, document id = record id, fields = the record's JSON fields. Names can be prefixed with
 * {@code app.firestore.collection-prefix} to keep separate data sets in one project.
 *
 * <p>Keeping H2's guarantees:
 *
 * <ul>
 *   <li><b>All or nothing.</b> Inside a transaction, writes are staged in {@link StagedWrites} and
 *       sent as one Firestore batch just before commit. If the method throws, nothing is sent.
 *   <li><b>One write at a time.</b> {@link #lock()} takes a lock held until the transaction ends.
 *       It covers this backend process only: two backends writing to the same Firestore at the same
 *       moment can still overwrite each other, so run one backend per shared data set.
 *   <li><b>Speed.</b> Reads come from {@link FirestoreMirror}, a live in-memory copy that Firestore
 *       keeps up to date, instead of a network round trip per read.
 * </ul>
 *
 * Bean registration is in {@link FirestoreStorageConfig}.
 */
class FirestoreStudyRepository implements StudyRepository {
    /** Firestore's limit on writes in one batch. */
    static final int MAX_BATCH_WRITES = 500;

    private static final Logger LOG = LoggerFactory.getLogger(FirestoreStudyRepository.class);
    private static final long TIMEOUT_SECONDS = 20;
    private static final TypeReference<Map<String, Object>> DOCUMENT = new TypeReference<>() {};

    private final Firestore firestore;
    private final FirestoreMirror mirror;
    private final ObjectMapper json;
    private final String collectionPrefix;
    private final ReentrantLock writeLock = new ReentrantLock();

    FirestoreStudyRepository(
            Firestore firestore, FirestoreMirror mirror, ObjectMapper json, String collectionPrefix) {
        this.firestore = firestore;
        this.mirror = mirror;
        this.json = json;
        this.collectionPrefix = collectionPrefix;
    }

    @Override
    public void lock() {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("lock() must be called inside a @Transactional method.");
        if (writeLock.isHeldByCurrentThread()) return;
        writeLock.lock();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        writeLock.unlock();
                    }
                });
    }

    @Override
    public <T> List<T> all(Class<T> type) {
        String kind = type.getSimpleName();
        Map<String, Map<String, Object>> stored = mirrorRead(() -> mirror.documents(kind));
        StagedWrites staged = currentStagedWrites();
        Map<String, Map<String, Object>> visible =
                staged == null ? stored : staged.overlay(kind, stored);
        List<T> result = new ArrayList<>(visible.size());
        visible.values().forEach(payload -> result.add(decode(payload, type)));
        return result;
    }

    @Override
    public <T> Optional<T> find(Class<T> type, String id) {
        String kind = type.getSimpleName();
        StagedWrites staged = currentStagedWrites();
        Map<String, Object> payload =
                staged != null && staged.touches(kind, id)
                        ? staged.staged(kind, id)
                        : mirrorRead(() -> mirror.document(kind, id));
        return Optional.ofNullable(payload).map(p -> decode(p, type));
    }

    @Override
    public <T> T save(String id, T value) {
        String kind = value.getClass().getSimpleName();
        Map<String, Object> payload = json.convertValue(value, DOCUMENT);
        StagedWrites staged = currentStagedWrites();
        if (staged != null) {
            staged.save(kind, id, payload);
        } else {
            WriteResult result = await(document(kind, id).set(payload));
            mirror.applyCommitted(kind, id, payload, result.getUpdateTime());
        }
        return value;
    }

    @Override
    public void delete(Class<?> type, String id) {
        String kind = type.getSimpleName();
        StagedWrites staged = currentStagedWrites();
        if (staged != null) {
            staged.delete(kind, id);
        } else {
            WriteResult result = await(document(kind, id).delete());
            mirror.applyCommitted(kind, id, null, result.getUpdateTime());
        }
    }

    /**
     * The staged writes of the current transaction, created on first use; {@code null} outside a
     * transaction, where writes go straight to Firestore.
     */
    private StagedWrites currentStagedWrites() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return null;
        StagedWrites staged = (StagedWrites) TransactionSynchronizationManager.getResource(this);
        if (staged != null) return staged;
        StagedWrites created = new StagedWrites();
        TransactionSynchronizationManager.bindResource(this, created);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        commit(created);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        TransactionSynchronizationManager.unbindResourceIfPossible(
                                FirestoreStudyRepository.this);
                    }
                });
        return created;
    }

    /** Sends every staged change in one atomic batch. A failure here rolls the transaction back. */
    private void commit(StagedWrites staged) {
        if (staged.size() == 0) return;
        if (staged.size() > MAX_BATCH_WRITES)
            throw new IllegalStateException(
                    "One action changed "
                            + staged.size()
                            + " records; Firestore accepts at most "
                            + MAX_BATCH_WRITES
                            + " in one batch.");
        WriteBatch batch = firestore.batch();
        staged.forEach(
                (kind, id, payload) -> {
                    if (payload == null) batch.delete(document(kind, id));
                    else batch.set(document(kind, id), payload);
                });
        List<WriteResult> results = await(batch.commit());
        // Results come back in the order the writes were added.
        int[] next = {0};
        staged.forEach(
                (kind, id, payload) ->
                        mirror.applyCommitted(
                                kind, id, payload, results.get(next[0]++).getUpdateTime()));
    }

    private DocumentReference document(String kind, String id) {
        return firestore.collection(collectionPrefix + kind).document(id);
    }

    private <T> T decode(Map<String, Object> payload, Class<T> type) {
        return json.convertValue(payload, type);
    }

    /** Waits for Firestore, turning network problems into a readable 503 for the user. */
    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        } catch (ExecutionException | TimeoutException e) {
            throw unavailable(e);
        }
    }

    /** The mirror only fails while first loading a collection, e.g. when offline. */
    private static <T> T mirrorRead(java.util.function.Supplier<T> read) {
        try {
            return read.get();
        } catch (IllegalStateException e) {
            throw unavailable(e);
        }
    }

    private static DomainException unavailable(Exception cause) {
        LOG.warn("Firestore request failed", cause);
        DomainException e =
                new DomainException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The cloud database could not be reached. Check the internet connection and try again.");
        e.initCause(cause);
        return e;
    }
}
