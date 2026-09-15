package studybuddy.backend.common;

import org.springframework.http.HttpStatus;

/** Business failures carry a stable HTTP status without exposing database details. */
public class DomainException extends RuntimeException {
    private final HttpStatus status;

    public DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static DomainException missing(String message) {
        return new DomainException(HttpStatus.NOT_FOUND, message);
    }

    public static DomainException forbidden(String message) {
        return new DomainException(HttpStatus.FORBIDDEN, message);
    }

    public static DomainException conflict(String message) {
        return new DomainException(HttpStatus.CONFLICT, message);
    }
}
