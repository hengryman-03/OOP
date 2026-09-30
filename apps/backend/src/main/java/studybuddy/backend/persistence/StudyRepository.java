package studybuddy.backend.persistence;

import java.util.List;
import java.util.Optional;

/**
 * Stores template POJO aggregates as documents keyed by type and id; domain rules remain in
 * services. Reads return fresh objects, so changing a returned object has no effect until it is
 * saved again.
 *
 * <p>Implementations: {@link FirestoreStudyRepository} (the shared cloud database used by the app)
 * and {@link InMemoryStudyRepository} (a throwaway store used by automated tests).
 */
public interface StudyRepository {

    /**
     * Serializes business writes: only one {@link StudyTransactional} action that has called this
     * can commit at a time. This small demo favors predictable cross-aggregate integrity over
     * concurrent write throughput. Has no effect outside a transaction.
     */
    void lock();

    <T> List<T> all(Class<T> type);

    <T> Optional<T> find(Class<T> type, String id);

    <T> T save(String id, T value);

    void delete(Class<?> type, String id);

    /**
     * Runs {@code work} so that all of its saves and deletes are applied together, or not at all
     * if it throws. Reads inside {@code work} see its own pending changes. Joins the current
     * transaction if one is already active. Implementations may run {@code work} more than once if
     * another transaction conflicts with it, so it should not depend on side effects of an earlier
     * attempt.
     */
    Object inTransaction(TransactionWork work) throws Throwable;

    @FunctionalInterface
    interface TransactionWork {
        Object run() throws Throwable;
    }
}
