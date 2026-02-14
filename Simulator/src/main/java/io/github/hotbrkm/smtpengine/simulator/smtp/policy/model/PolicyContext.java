package io.github.hotbrkm.smtpengine.simulator.smtp.policy.model;

import lombok.Builder;
import lombok.Getter;
import org.subethamail.smtp.MessageContext;

import java.util.HashMap;
import java.util.Map;

@Getter
@Builder
public class PolicyContext {

    private final MessageContext messageContext;
    private final String mailFrom;
    private final String currentRecipient;
    private final int sessionRcptCount;
    private final int totalRcptCount;
    private final SmtpPhase phase;
    private final byte[] rawMessageBytes;

    @Builder.Default
    private final Map<String, Object> attributes = new HashMap<>();

    public void putAttribute(String key, Object value) {
        if (key == null) {
            return;
        }
        attributes.put(key, value);
    }

    public String getStringAttribute(String key) {
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public boolean getBooleanAttribute(String key, boolean defaultValue) {
        Object value = attributes.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }
}
