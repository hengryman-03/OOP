package studybuddy.backend.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Saves and deletes made during one transaction, held in memory until it commits. Reads in the
 * same transaction see these changes on top of what is stored; a rollback simply discards them.
 * Payloads are document maps, so every read decodes a fresh object.
 *
 * <p>Pure Java with no Firestore dependency, so the staging rules are unit-tested directly.
 */
final class StagedWrites {
    /** Key: kind, then id. A {@code null} payload marks a staged delete. */
    private final Map<String, Map<String, Map<String, Object>>> changes = new LinkedHashMap<>();

    void save(String kind, String id, Map<String, Object> payload) {
        changes.computeIfAbsent(kind, k -> new LinkedHashMap<>()).put(id, payload);
    }

    void delete(String kind, String id) {
        changes.computeIfAbsent(kind, k -> new LinkedHashMap<>()).put(id, null);
    }

    /** Whether this transaction saved or deleted the record. */
    boolean touches(String kind, String id) {
        return changes.getOrDefault(kind, Map.of()).containsKey(id);
    }

    /** The staged payload, or {@code null} when the record was deleted. Check {@link #touches}. */
    Map<String, Object> staged(String kind, String id) {
        return changes.getOrDefault(kind, Map.of()).get(id);
    }

    /** Stored documents with this transaction's changes applied, ordered by id. */
    Map<String, Map<String, Object>> overlay(String kind, Map<String, Map<String, Object>> stored) {
        Map<String, Map<String, Object>> merged = new TreeMap<>(stored);
        changes.getOrDefault(kind, Map.of())
                .forEach(
                        (id, payload) -> {
                            if (payload == null) merged.remove(id);
                            else merged.put(id, payload);
                        });
        return merged;
    }

    int size() {
        return changes.values().stream().mapToInt(Map::size).sum();
    }

    /** Visits every change in the order it was first made; a {@code null} payload is a delete. */
    void forEach(ChangeVisitor visitor) {
        changes.forEach((kind, ofKind) -> ofKind.forEach((id, p) -> visitor.visit(kind, id, p)));
    }

    @FunctionalInterface
    interface ChangeVisitor {
        void visit(String kind, String id, Map<String, Object> payloadOrNullForDelete);
    }
}
