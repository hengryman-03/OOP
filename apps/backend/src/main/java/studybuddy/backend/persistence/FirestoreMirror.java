package studybuddy.backend.persistence;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentChange;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.ListenerRegistration;

import org.springframework.beans.factory.DisposableBean;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A live in-memory copy of the Firestore collections this backend uses (Observer pattern).
 *
 * <p>The first time a kind is read, a Firestore snapshot listener is attached to its collection.
 * Firestore sends the whole collection once, then pushes every later change, including changes
 * made by other backends (other laptops), usually within a second. Reads are then served from
 * memory instead of a slow network round trip, and only changed documents count as Firestore
 * reads.
 *
 * <p>This backend's own commits are applied immediately ({@link #applyCommitted}), so a refresh
 * right after an action always shows it. Every entry carries the Firestore time it was written,
 * and an update is only applied if it is newer, so a late listener event can never undo a newer
 * write.
 */
class FirestoreMirror implements DisposableBean {
    private static final long FIRST_LOAD_TIMEOUT_SECONDS = 20;

    /** One document's latest known state; {@code payload == null} records a deletion. */
    private record Entry(Map<String, Object> payload, Timestamp version) {}

    /** The mirror of one collection and the listener that keeps it current. */
    private static final class Collection {
        final Map<String, Entry> entries = new ConcurrentHashMap<>();
        final CountDownLatch firstLoad = new CountDownLatch(1);
        volatile ListenerRegistration listener;
        volatile Exception failure;
    }

    private final Firestore firestore;
    private final String collectionPrefix;
    private final Map<String, Collection> collections = new ConcurrentHashMap<>();

    FirestoreMirror(Firestore firestore, String collectionPrefix) {
        this.firestore = firestore;
        this.collectionPrefix = collectionPrefix;
    }

    /** Current documents of a kind, ordered by id. Waits for the first load if needed. */
    Map<String, Map<String, Object>> documents(String kind) {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        loaded(kind).entries.forEach(
                (id, entry) -> {
                    if (entry.payload() != null) result.put(id, entry.payload());
                });
        return result;
    }

    /** One document's fields, or {@code null} when it does not exist. */
    Map<String, Object> document(String kind, String id) {
        Entry entry = loaded(kind).entries.get(id);
        return entry == null ? null : entry.payload();
    }

    /** Records a write this backend has just committed, so it is visible without waiting. */
    void applyCommitted(String kind, String id, Map<String, Object> payload, Timestamp version) {
        Collection collection = collections.get(kind);
        if (collection != null) apply(collection, id, payload, version);
    }

    private Collection loaded(String kind) {
        Collection collection = collections.computeIfAbsent(kind, this::listen);
        try {
            if (!collection.firstLoad.await(FIRST_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out loading " + kind + " from Firestore");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted loading " + kind, e);
        }
        if (collection.failure != null) {
            // Forget the broken listener so the next read tries again.
            if (collection.listener != null) collection.listener.remove();
            collections.remove(kind, collection);
            throw new IllegalStateException("Firestore listener failed for " + kind, collection.failure);
        }
        return collection;
    }

    private Collection listen(String kind) {
        Collection collection = new Collection();
        collection.listener =
                firestore
                        .collection(collectionPrefix + kind)
                        .addSnapshotListener(
                                (snapshot, error) -> {
                                    if (error != null) {
                                        collection.failure = error;
                                    } else {
                                        for (DocumentChange change : snapshot.getDocumentChanges()) {
                                            var doc = change.getDocument();
                                            if (change.getType() == DocumentChange.Type.REMOVED)
                                                apply(collection, doc.getId(), null, snapshot.getReadTime());
                                            else
                                                apply(collection, doc.getId(), doc.getData(), doc.getUpdateTime());
                                        }
                                    }
                                    collection.firstLoad.countDown();
                                });
        return collection;
    }

    private static void apply(
            Collection collection, String id, Map<String, Object> payload, Timestamp version) {
        collection.entries.compute(
                id,
                (key, current) ->
                        current == null || version.compareTo(current.version()) > 0
                                ? new Entry(payload, version)
                                : current);
    }

    @Override
    public void destroy() {
        collections.values().forEach(c -> {
            if (c.listener != null) c.listener.remove();
        });
    }
}
