package io.github.hotbrkm.smtpengine.agent.email.send.result;

/**
 * Exception indicating failure to persist send results (checkpoint/intermediate save).
 * <p>
 * This exception is considered a fatal error and may trigger task retry by the caller.
 */
public class ResultPersistenceException extends RuntimeException {
    public ResultPersistenceException(String message) {
        super(message);
    }

    public ResultPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
