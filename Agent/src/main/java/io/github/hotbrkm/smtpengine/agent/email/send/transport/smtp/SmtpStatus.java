package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

/**
 * SMTP/transport status code constants and exception mapping utility
 */
public final class SmtpStatus {

    private SmtpStatus() {
    }

    /** Success */
    public static final int OK = 250;
    /** Temporary error (retry) */
    public static final int TEMPORARY_FAILURE = 421;
    /** Session invalid/not open */
    public static final int SESSION_INVALID = 888;
    /** MIME build failed */
    public static final int MIME_BUILD_FAILED = 900;
    /** Unknown error (default) */
    public static final int UNKNOWN_ERROR = 700;

    /**
     * Maps exceptions to transport status codes.
     */
    public static int fromException(Throwable e) {
        if (e == null) {
            return UNKNOWN_ERROR;
        }

        // Network-related exceptions are treated as temporary errors for retry classification
        if (e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.ConnectException
                || e instanceof java.net.NoRouteToHostException) {
            return TEMPORARY_FAILURE;
        }

        // Session state issues
        if (e instanceof IllegalStateException) {
            return SESSION_INVALID;
        }

        return UNKNOWN_ERROR;
    }
}

