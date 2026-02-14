package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import java.util.Objects;

public record EmailSendContext(
        long messageId, long resultSeq, int groupSeq, String messageType, String templateSubtype, ExecutionMode executionMode
) {
    public EmailSendContext {
        Objects.requireNonNull(messageType, "messageType must not be null");
        Objects.requireNonNull(templateSubtype, "templateSubtype must not be null");
        Objects.requireNonNull(executionMode, "executionMode must not be null");
    }

    public String toRunnerId() {
        return messageId + "-" + resultSeq + "-" + groupSeq;
    }
}
