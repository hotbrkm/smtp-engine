package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import lombok.Getter;

/**
 * Exception to preserve cause code/message when SMTP session open fails.
 */
@Getter
public class SmtpSessionOpenException extends RuntimeException {
    private final int statusCode;
    private final String originalMessage;
    private final String bindIp;
    private final boolean localBindError;

    public SmtpSessionOpenException(int statusCode, String originalMessage, String bindIp, boolean localBindError) {
        super(originalMessage);
        this.statusCode = statusCode;
        this.originalMessage = originalMessage;
        this.bindIp = bindIp;
        this.localBindError = localBindError;
    }
}
