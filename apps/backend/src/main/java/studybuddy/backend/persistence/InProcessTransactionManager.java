package studybuddy.backend.persistence;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Lets the existing {@code @Transactional} annotations work when there is no SQL database.
 *
 * <p>It only marks where each transaction starts and ends. Spring then calls back registered
 * synchronizations at commit or rollback, which is where {@link FirestoreStudyRepository} sends
 * or discards its staged writes and releases its lock. Joining an outer transaction (the default
 * {@code REQUIRED} propagation) is supported; suspending one ({@code REQUIRES_NEW}) is not, and
 * no service uses it.
 */
class InProcessTransactionManager extends AbstractPlatformTransactionManager {

    /** Shared by the outer transaction and every method that joins it. */
    private static final class State {
        private boolean rollbackOnly;
    }

    /** Spring's handle for one transactional call; it points at the shared state once begun. */
    private static final class Transaction implements SmartTransactionObject {
        private State state;

        @Override
        public boolean isRollbackOnly() {
            return state != null && state.rollbackOnly;
        }

        @Override
        public void flush() {}
    }

    @Override
    protected Object doGetTransaction() {
        Transaction transaction = new Transaction();
        transaction.state = (State) TransactionSynchronizationManager.getResource(this);
        return transaction;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((Transaction) transaction).state != null;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        State state = new State();
        ((Transaction) transaction).state = state;
        TransactionSynchronizationManager.bindResource(this, state);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        // Staged writes were already sent in beforeCommit, so a failure there still rolls back.
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // Staged writes are discarded when the synchronization completes.
    }

    /** A joined method failed: the outer transaction must roll back instead of committing. */
    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        ((State) TransactionSynchronizationManager.getResource(this)).rollbackOnly = true;
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        TransactionSynchronizationManager.unbindResourceIfPossible(this);
    }
}
