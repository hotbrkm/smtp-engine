package io.github.hotbrkm.smtpengine.simulator.smtp.exception;

import org.subethamail.smtp.RejectException;

/**
 * Special exception indicating that forced termination of the TCP connection is required.
 *
 * Since the SubEtha SMTP library does not provide socket access at the MessageHandler level,
 * this exception notifies the upper layer that connection termination is necessary.
 *
 * According to RFC 2821, a client receiving a 421 response must close the connection immediately,
 * and SubEtha SMTP also automatically terminates the session after a 421 response.
 */
public class DisconnectException extends RejectException {

    private final boolean shouldCloseConnection;

    /**
     * Creates a DisconnectException.
     *
     * @param code SMTP response code (typically 421)
     * @param message Response message (including reason suffix)
     * @param shouldCloseConnection Whether to close the connection
     */
    public DisconnectException(int code, String message, boolean shouldCloseConnection) {
        super(code, message);
        this.shouldCloseConnection = shouldCloseConnection;
    }

    /**
     * Whether the TCP connection should be closed.
     */
    public boolean shouldCloseConnection() {
        return shouldCloseConnection;
    }
}
