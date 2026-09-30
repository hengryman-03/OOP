package studybuddy.backend.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose repository changes must all succeed or all be discarded. It is the
 * repository-level replacement for Spring's {@code @Transactional}; see {@link
 * StudyRepository#inTransaction}. Like {@code @Transactional}, it only applies when the method is
 * called from another bean, not from within the same class.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StudyTransactional {}
