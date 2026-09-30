package studybuddy.backend.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The saves and deletes made during one transaction. They are held back until the transaction
 * commits, and reads inside the transaction consult them first so the transaction sees its own
 * changes.
 */
final class StagedWrites {
    record Key(String kind, String id) {}

    @FunctionalInterface
    interface Consumer {
        /** {@code document} is {@code null} for a delete. */
        void accept(String kind, String id, Map<String, Object> document);
    }

    private final Map<Key, Map<String, Object>> changes = new LinkedHashMap<>();

    void save(String kind, String id, Map<String, Object> document) {
        changes.put(new Key(kind, id), document);
    }

    void delete(String kind, String id) {
        changes.put(new Key(kind, id), null);
    }

    boolean contains(String kind, String id) {
        return changes.containsKey(new Key(kind, id));
    }

    /** The staged document, or {@code null} if it was deleted in this transaction. */
    Map<String, Object> get(String kind, String id) {
        return changes.get(new Key(kind, id));
    }

    /** Applies this transaction's changes on top of committed documents keyed and sorted by id. */
    TreeMap<String, Map<String, Object>> overlay(String kind, Map<String, Map<String, Object>> committed) {
        TreeMap<String, Map<String, Object>> result = new TreeMap<>(committed);
        changes.forEach(
                (key, document) -> {
                    if (!key.kind().equals(kind)) return;
                    if (document == null) result.remove(key.id());
                    else result.put(key.id(), document);
                });
        return result;
    }

    void forEach(Consumer consumer) {
        changes.forEach((key, document) -> consumer.accept(key.kind(), key.id(), document));
    }
}
