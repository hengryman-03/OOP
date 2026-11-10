package studybuddy.backend.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Storage contract every service depends on. Each record ("aggregate") is stored whole under its
 * class simple name ("kind") and an id. Relationships between records are enforced by services.
 *
 * <p>Two implementations exist and one is chosen at startup with {@code APP_STORAGE}:
 * {@link H2StudyRepository} (default, local file) and {@link FirestoreStudyRepository} (shared
 * cloud database). Services never know which one they received.
 *
 * <p>Write rules for callers: change data inside a {@code @Transactional} method and call {@link
 * #lock()} before reading business state. If the method throws, none of its writes are kept.
 */
public interface StudyRepository {

    /** Serializes business writes so duplicate, capacity and cross-record checks stay atomic. */
    void lock();

    /** Every stored record of this type, ordered by id. */
    <T> List<T> all(Class<T> type);

    <T> Optional<T> find(Class<T> type, String id);

    /** Inserts or replaces the record stored under this id and returns it. */
    <T> T save(String id, T value);

    void delete(Class<?> type, String id);
}
