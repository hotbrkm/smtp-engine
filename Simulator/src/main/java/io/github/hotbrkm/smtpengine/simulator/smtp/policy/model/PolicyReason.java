package io.github.hotbrkm.smtpengine.simulator.smtp.policy.model;

public enum PolicyReason {
    CONNECTION_LIMIT,
    RATE_LIMIT,
    ADAPTIVE_RATE_CONTROL,
    RESPONSE_DELAY,
    GREYLISTING,
    DISCONNECT_RULE,
    MAIL_AUTH_CHECK,
    FAULT_INJECTION,
    NONE
}
