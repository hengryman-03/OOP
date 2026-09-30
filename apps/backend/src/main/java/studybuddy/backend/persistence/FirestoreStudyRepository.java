package studybuddy.backend.persistence;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.Transaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores each aggregate type as a Cloud Firestore collection named after the class (e.g. {@code
 * StudentProfile/S001}), shared by everyone running the backend against the same Firebase project.
 *
 * <p>Transactions use Firestore transactions. Firestore requires every read in a transaction to
 * happen before any write, so saves and deletes are staged and written together at the end. {@link
 * #lock()} reads and rewrites a single {@code _meta/lock} document, so any two business writes
 * conflict and Firestore commits them one after the other, retrying the later one.
 */
public class FirestoreStudyRepository implements StudyRepository {
    private static final String META_COLLECTION = "_meta";
    private static final String LOCK_DOCUMENT = "lock";
    private static final long TIMEOUT_SECONDS = 30;

    private record ActiveTransaction(Transaction transaction, StagedWrites staged) {}

    private final Firestore firestore;
    private final DocumentCodec codec;
    private final ThreadLocal<ActiveTransaction> current = new ThreadLocal<>();

    public FirestoreStudyRepository(Firestore firestore, DocumentCodec codec) {
        this.firestore = firestore;
        this.codec = codec;
    }

    @Override
    public void lock() {
        ActiveTransaction active = current.get();
        if (active == null) return;
        DocumentReference lock = firestore.collection(META_COLLECTION).document(LOCK_DOCUMENT);
        await(active.transaction().get(lock));
        active.staged()
                .save(META_COLLECTION, LOCK_DOCUMENT, Map.of("lastWriteAt", FieldValue.serverTimestamp()));
    }

    @Override
    public <T> List<T> all(Class<T> type) {
        String kind = codec.kindOf(type);
        ActiveTransaction active = current.get();
        Query query = firestore.collection(kind).orderBy(FieldPath.documentId());
        List<QueryDocumentSnapshot> snapshots =
                await(active == null ? query.get() : active.transaction().get(query)).getDocuments();
        Map<String, Map<String, Object>> documents = new LinkedHashMap<>();
        for (QueryDocumentSnapshot snapshot : snapshots) documents.put(snapshot.getId(), snapshot.getData());
        if (active != null) documents = active.staged().overlay(kind, documents);
        return documents.values().stream().map(document -> codec.decode(document, type)).toList();
    }

    @Override
    public <T> Optional<T> find(Class<T> type, String id) {
        String kind = codec.kindOf(type);
        ActiveTransaction active = current.get();
        if (active != null && active.staged().contains(kind, id))
            return Optional.ofNullable(active.staged().get(kind, id)).map(d -> codec.decode(d, type));
        DocumentReference reference = firestore.collection(kind).document(id);
        DocumentSnapshot snapshot =
                await(active == null ? reference.get() : active.transaction().get(reference));
        return snapshot.exists() ? Optional.of(codec.decode(snapshot.getData(), type)) : Optional.empty();
    }

    @Override
    public <T> T save(String id, T value) {
        String kind = codec.kindOf(value.getClass());
        Map<String, Object> document = codec.encode(id, value);
        ActiveTransaction active = current.get();
        if (active != null) active.staged().save(kind, id, document);
        else await(firestore.collection(kind).document(id).set(document));
        return value;
    }

    @Override
    public void delete(Class<?> type, String id) {
        String kind = codec.kindOf(type);
        ActiveTransaction active = current.get();
        if (active != null) active.staged().delete(kind, id);
        else await(firestore.collection(kind).document(id).delete());
    }

    @Override
    public Object inTransaction(TransactionWork work) throws Throwable {
        if (current.get() != null) return work.run();
        // Firestore runs the callback on its own thread and may retry it; remember the last
        // attempt's exception so the caller gets the original (e.g. a DomainException) back.
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            return await(
                    firestore.runTransaction(
                            transaction -> {
                                failure.set(null);
                                StagedWrites staged = new StagedWrites();
                                current.set(new ActiveTransaction(transaction, staged));
                                try {
                                    Object result = work.run();
                                    staged.forEach(
                                            (kind, id, document) -> {
                                                DocumentReference reference =
                                                        firestore.collection(kind).document(id);
                                                if (document == null) transaction.delete(reference);
                                                else transaction.set(reference, document);
                                            });
                                    return result;
                                } catch (Exception e) {
                                    failure.set(e);
                                    throw e;
                                } catch (Throwable e) {
                                    failure.set(e);
                                    throw new IllegalStateException(e);
                                } finally {
                                    current.remove();
                                }
                            }));
        } catch (RuntimeException e) {
            Throwable original = failure.get();
            throw original != null ? original : e;
        }
    }

    /** Waits for a Firestore call, rethrowing its failure unwrapped so Firestore can retry aborts. */
    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException("Firestore request failed.", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Firestore.", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Firestore did not respond in time.", e);
        }
    }
}
