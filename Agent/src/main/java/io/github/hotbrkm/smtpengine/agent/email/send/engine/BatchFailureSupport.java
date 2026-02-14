package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

/**
 * Provides utility methods commonly used by {@link ResourceGate} and {@link ExecutionPermitService}
 * in final batch failure handling.
 */
@UtilityClass
final class BatchFailureSupport {

    /**
     * Determines the final status code from the sendCode of targets already processed in the batch.
     * Returns {@code fallbackCode} if no valid code exists.
     */
    static int resolveFinalStatusCode(DomainBatchTask task, int fallbackCode) {
        for (EmailSendTarget target : task.getBatch()) {
            String code = target.getSendCode();
            // Excludes unset codes, unprocessed targets, and successful sends (250) from failure code candidates.
            if (code == null || target.isUnprocessed() || "250".equals(code)) {
                continue;
            }
            try {
                return Integer.parseInt(code);
            } catch (Exception ignore) {
                // ignore and fallback
            }
        }
        return fallbackCode;
    }

    /**
     * Determines the final batch failure message.
     * <p>
     * Returns the pre-formatted {@code timeoutMessage} if {@code statusCode} equals {@code timeoutCode},
     * otherwise searches for a matching error message in the batch targets.
     */
    static String resolveFinalMessage(DomainBatchTask task, int statusCode, int timeoutCode, String timeoutMessage) {
        if (statusCode == timeoutCode) {
            return timeoutMessage;
        }
        for (EmailSendTarget target : task.getBatch()) {
            String code = target.getSendCode();
            if (code == null) {
                continue;
            }
            if (String.valueOf(statusCode).equals(code)) {
                String error = target.getErrorMessage();
                if (error != null && !error.isBlank()) {
                    return error;
                }
            }
        }
        return statusCode + " retry exhausted";
    }

    /**
     * Increments the retry count of all targets in the batch by 1.
     */
    static void incrementRetryCount(List<EmailSendTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (EmailSendTarget target : targets) {
            target.incrementRetryCount();
        }
    }

    /**
     * Calculates requeue delay time. Mitigates thundering herd using fixed delay + jitter.
     */
    static long computeRequeueDelayMillis(long baseDelayMs, long jitterMs) {
        long jitter = ThreadLocalRandom.current().nextLong(jitterMs + 1L);
        return baseDelayMs + jitter;
    }
}
