package studybuddy.backend.persistence;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/** Runs every {@link StudyTransactional} method inside a repository transaction. */
@Aspect
@Component
public class StudyTransactionAspect {
    private final StudyRepository repository;

    public StudyTransactionAspect(StudyRepository repository) {
        this.repository = repository;
    }

    @Around("@annotation(studybuddy.backend.persistence.StudyTransactional)")
    public Object runInTransaction(ProceedingJoinPoint call) throws Throwable {
        return repository.inTransaction(call::proceed);
    }
}
