package io.github.hotbrkm.smtpengine.agent.email.send.result;

/**
 * Snapshot of sending progress/result for a single target.
 */
public record EmailSendProgress(int listSeq, String targetEmail, String sendCode, String sendStatus, String errorMessage,
        String endDateTime, String emailDomain, int retryCount) {
}

