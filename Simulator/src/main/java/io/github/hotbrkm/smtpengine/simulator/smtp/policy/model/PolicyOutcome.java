package io.github.hotbrkm.smtpengine.simulator.smtp.policy.model;

import org.subethamail.smtp.RejectException;

/**
 * Represents the outcome of a policy evaluation.
 * <p>
 * This record contains all information about how the SMTP server should
 * respond to a client after evaluating policy rules.
 * </p>
 *
 * @author hotbrkm
 * @since 1.0.0
 */
public record PolicyOutcome(PolicyDecision decision,
                            int smtpCode,
                            String message,
                            PolicyReason reason,
                            boolean closeConnection,
                            long delayMillis,
                            boolean synthetic) {

    /**
     * Creates an allow outcome.
     * @return policy outcome that allows the operation
     */
    public static PolicyOutcome allow() {
        return new PolicyOutcome(PolicyDecision.ALLOW, 0, null, PolicyReason.NONE, false, 0L, false);
    }

    /**
     * Creates an outcome that delays the response.
     * @param delayMillis delay in milliseconds
     * @param reason the reason for delay
     * @return policy outcome with delay
     */
    public static PolicyOutcome delay(long delayMillis, PolicyReason reason) {
        return new PolicyOutcome(PolicyDecision.ALLOW, 0, null, reason == null ? PolicyReason.NONE : reason,
                false, Math.max(0L, delayMillis), false);
    }

    /**
     * Creates a temporary failure outcome (4xx response).
     * @param code SMTP response code
     * @param message response message
     * @param reason the reason for failure
     * @return policy outcome for temporary failure
     */
    public static PolicyOutcome tempFail(int code, String message, PolicyReason reason) {
        return new PolicyOutcome(PolicyDecision.TEMP_FAIL, code, message, reason, false, 0L, false);
    }

    /**
     * Creates a permanent failure outcome (5xx response).
     * @param code SMTP response code
     * @param message response message
     * @param reason the reason for failure
     * @return policy outcome for permanent failure
     */
    public static PolicyOutcome permFail(int code, String message, PolicyReason reason) {
        return new PolicyOutcome(PolicyDecision.PERM_FAIL, code, message, reason, false, 0L, false);
    }

    /**
     * Creates a disconnect outcome.
     * @param code SMTP response code
     * @param message response message
     * @param reason the reason for disconnection
     * @return policy outcome for disconnection
     */
    public static PolicyOutcome disconnect(int code, String message, PolicyReason reason) {
        return new PolicyOutcome(PolicyDecision.DISCONNECT, code, message, reason, true, 0L, false);
    }

    /**
     * Returns a new outcome with the specified delay.
     * @param value delay in milliseconds
     * @return new outcome with delay
     */
    public PolicyOutcome withDelay(long value) {
        return new PolicyOutcome(decision, smtpCode, message, reason, closeConnection, Math.max(0L, value), synthetic);
    }

    /**
     * Returns a new outcome with the synthetic flag set.
     * @param value synthetic flag
     * @return new outcome with synthetic flag
     */
    public PolicyOutcome withSynthetic(boolean value) {
        return new PolicyOutcome(decision, smtpCode, message, reason, closeConnection, delayMillis, value);
    }

    /**
     * Checks if this outcome is terminal (stops processing).
     * @return true if this is a terminal outcome
     */
    public boolean isTerminal() {
        return decision == PolicyDecision.TEMP_FAIL
                || decision == PolicyDecision.PERM_FAIL
                || decision == PolicyDecision.DISCONNECT;
    }

    /**
     * Converts this outcome to a RejectException if applicable.
     * @return RejectException or null if not applicable
     */
    public RejectException toRejectException() {
        if (decision != PolicyDecision.TEMP_FAIL && decision != PolicyDecision.PERM_FAIL) {
            return null;
        }
        return new RejectException(smtpCode, message);
    }
}
