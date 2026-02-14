package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum SmtpCommand {
    CONNECT("CONNECT", 250),
    INIT("INIT", 220),
    HELO("HELO", 250),
    EHLO("EHLO", 250),
    STARTTLS("STARTTLS", 220),
    MAIL_FROM("MAIL FROM:", 250),
    RCPT_TO("RCPT TO:", 250),
    DATA("DATA", 354),
    DATA_END("DATA_END", 250),  // Response after message transmission (250)
    RSET("RSET", 250),
    QUIT("QUIT", 221);

    private final String command;
    private final int successCode;

    public String buildMessage(String message) {
        if (message == null || message.isEmpty()) {
            return command;
        }

        // MAIL FROM:, RCPT TO: etc. already contain colon, so concatenate without space
        if (command.endsWith(":")) {
            return command + message;
        }

        return command + " " + message;
    }

    @Override
    public String toString() {
        return command;
    }
}
