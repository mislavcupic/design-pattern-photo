package hr.algebra.nrako.photoapp_backend.exceptions;

public class FailedToGetUsersException extends RuntimeException{
    public FailedToGetUsersException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
