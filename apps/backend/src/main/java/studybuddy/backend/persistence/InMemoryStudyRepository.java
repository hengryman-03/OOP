package studybuddy.backend.persistence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps documents in memory for automated tests: nothing leaves the process and every test run
 * starts empty. Transactions behave like the Firestore ones: changes are staged and applied
 * together on success, discarded on failure, and {@link #lock()} holds one lock until the
 * transaction ends.
 */
public class InMemoryStudyRepository implements StudyRepository {
    private final DocumentCodec codec;
    private final Map<String, TreeMap<String, Map<String, Object>>> collections = new HashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ThreadLocal<StagedWrites> current = new ThreadLocal<>();

    public InMemoryStudyRepository(DocumentCodec codec) {
        this.codec = codec;
    }

    /** Removes every document; tests call this to start from a clean store. */
    public synchronized void clear() {
        collections.clear();
    }

    @Override
    public void lock() {
        if (current.get() != null && !writeLock.isHeldByCurrentThread()) writeLock.lock();
    }

    @Override
    public <T> List<T> all(Class<T> type) {
        String kind = codec.kindOf(type);
        Map<String, Map<String, Object>> documents = committed(kind);
        StagedWrites staged = current.get();
        if (staged != null) documents = staged.overlay(kind, documents);
        return documents.values().stream().map(document -> codec.decode(document, type)).toList();
    }

    @Override
    public <T> Optional<T> find(Class<T> type, String id) {
        String kind = codec.kindOf(type);
        StagedWrites staged = current.get();
        Map<String, Object> document =
                staged != null && staged.contains(kind, id) ? staged.get(kind, id) : committed(kind).get(id);
        return Optional.ofNullable(document).map(d -> codec.decode(d, type));
    }

    @Override
    public <T> T save(String id, T value) {
        String kind = codec.kindOf(value.getClass());
        Map<String, Object> document = codec.encode(id, value);
        StagedWrites staged = current.get();
        if (staged != null) staged.save(kind, id, document);
        else apply(kind, id, document);
        return value;
    }

    @Override
    public void delete(Class<?> type, String id) {
        String kind = codec.kindOf(type);
        StagedWrites staged = current.get();
        if (staged != null) staged.delete(kind, id);
        else apply(kind, id, null);
    }

    @Override
    public Object inTransaction(TransactionWork work) throws Throwable {
        if (current.get() != null) return work.run();
        StagedWrites staged = new StagedWrites();
        current.set(staged);
        try {
            Object result = work.run();
            synchronized (this) {
                staged.forEach(this::apply);
            }
            return result;
        } finally {
            current.remove();
            if (writeLock.isHeldByCurrentThread()) writeLock.unlock();
        }
    }

    private synchronized Map<String, Map<String, Object>> committed(String kind) {
        return new TreeMap<>(collections.getOrDefault(kind, new TreeMap<>()));
    }

    private synchronized void apply(String kind, String id, Map<String, Object> document) {
        TreeMap<String, Map<String, Object>> collection =
                collections.computeIfAbsent(kind, k -> new TreeMap<>());
        if (document == null) collection.remove(id);
        else collection.put(id, document);
    }
}
