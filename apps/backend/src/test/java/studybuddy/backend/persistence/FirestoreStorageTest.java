package studybuddy.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Firestore-mode rules that need no network: staged writes and transaction boundaries. The live
 * Firestore round trip is covered by {@code FirestoreLiveTest} when credentials are available.
 */
class FirestoreStorageTest {

    // --- StagedWrites: what a transaction sees before it commits

    @Test
    void stagedSaveAndDeleteOverlayStoredDocumentsInIdOrder() {
        StagedWrites staged = new StagedWrites();
        staged.save("StudyGroup", "G002", Map.of("name", "changed"));
        staged.save("StudyGroup", "G000", Map.of("name", "new"));
        staged.delete("StudyGroup", "G001");

        Map<String, Map<String, Object>> visible =
                staged.overlay(
                        "StudyGroup",
                        Map.of(
                                "G001", Map.of("name", "deleted"),
                                "G002", Map.of("name", "stored")));

        assertEquals(List.of("G000", "G002"), List.copyOf(visible.keySet()));
        assertEquals("changed", visible.get("G002").get("name"));
        assertEquals(3, staged.size());
    }

    @Test
    void stagedLookupDistinguishesUntouchedSavedAndDeleted() {
        StagedWrites staged = new StagedWrites();
        staged.save("Room", "R1", Map.of("capacity", 4));
        staged.delete("Room", "R2");

        assertFalse(staged.touches("Room", "R3"));
        assertTrue(staged.touches("Room", "R1"));
        assertEquals(4, staged.staged("Room", "R1").get("capacity"));
        assertTrue(staged.touches("Room", "R2"));
        assertNull(staged.staged("Room", "R2"));
        assertFalse(staged.touches("StudyGroup", "R1"), "kinds are kept apart");
    }

    @Test
    void laterChangeToTheSameRecordReplacesTheEarlierOne() {
        StagedWrites staged = new StagedWrites();
        staged.save("Course", "IS442", Map.of("title", "first"));
        staged.delete("Course", "IS442");
        staged.save("Course", "IS442", Map.of("title", "last"));

        List<String> sent = new ArrayList<>();
        staged.forEach((kind, id, payload) -> sent.add(kind + "/" + id + "=" + payload));
        assertEquals(List.of("Course/IS442={title=last}"), sent);
    }

    // --- InProcessTransactionManager: commit sends, rollback discards

    @Test
    void commitRunsBeforeCommitCallbackAndRollbackDoesNot() {
        TransactionTemplate tx = new TransactionTemplate(new InProcessTransactionManager());
        List<String> events = new ArrayList<>();

        tx.executeWithoutResult(status -> register(events));
        assertEquals(List.of("beforeCommit", "afterCompletion:committed"), events);

        events.clear();
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    register(events);
                                    throw new IllegalStateException("business rule failed");
                                }));
        assertEquals(List.of("afterCompletion:rolled back"), events);
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @Test
    void failedJoinedMethodPreventsTheOuterCommit() {
        TransactionTemplate tx = new TransactionTemplate(new InProcessTransactionManager());
        List<String> events = new ArrayList<>();

        assertThrows(
                UnexpectedRollbackException.class,
                () ->
                        tx.executeWithoutResult(
                                outer -> {
                                    register(events);
                                    try {
                                        tx.executeWithoutResult(
                                                inner -> {
                                                    throw new IllegalArgumentException("inner");
                                                });
                                    } catch (IllegalArgumentException swallowed) {
                                        // The outer method carries on, but must not commit.
                                    }
                                }));
        assertEquals(List.of("afterCompletion:rolled back"), events);
    }

    private static void register(List<String> events) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        events.add("beforeCommit");
                    }

                    @Override
                    public void afterCompletion(int status) {
                        events.add(
                                "afterCompletion:"
                                        + (status == STATUS_COMMITTED ? "committed" : "rolled back"));
                    }
                });
    }
}
