package io.github.hotbrkm.smtpengine.agent.email.send.worker;

import io.github.hotbrkm.smtpengine.agent.email.mime.EmailMimeComposer;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommandResponse;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpSessionOpenException;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpStatus;
import io.github.hotbrkm.smtpengine.agent.email.send.result.ResultPersistenceException;
import io.github.hotbrkm.smtpengine.agent.email.send.result.SendResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Class that sends email batches
 * Receives batch data grouped by domain and sends emails.
 * Implements Callable to run in parallel in a Thread Pool.
 */
@Slf4j
public class EmailBatchSender implements Callable<Integer> {

    private final List<EmailSendTarget> batch;
    @Getter
    private final String domain;
    @Getter
    private final String bindIp;
    private final SmtpSessionManager smtpSessionManager;
    private final EmailMimeComposer emailMimeComposer;
    private final ResultApplier resultApplier;
    private final List<String> retryExcludeCode;
    private final DomainSendMetrics domainSendMetrics;

    // State for session-level error propagation
    private boolean sessionBroken = false;
    @Getter
    private int lastErrorStatusCode = SmtpStatus.UNKNOWN_ERROR;
    @Getter
    private String lastErrorMessage = SmtpStatus.UNKNOWN_ERROR + " Unknown error";
    @Getter
    private boolean localBindFailure = false;
    private boolean hasAnySuccess = false;

    /**
     * Constructor that receives all dependencies needed for batch transmission.
     * <p>
     * In the Engine layer, it is recommended to call this constructor through {@link EmailBatchSenderFactory}.
     */
    public EmailBatchSender(List<EmailSendTarget> batch, String domain, String bindIp, SmtpSessionManager smtpSessionManager,
                            EmailMimeComposer emailMimeComposer, ResultApplier resultApplier, List<String> retryExcludeCode,
                            DomainSendMetrics domainSendMetrics) {
        this.batch = Objects.requireNonNull(batch, "batch must not be null");
        this.domain = Objects.requireNonNull(domain, "domain must not be null");
        this.bindIp = Objects.requireNonNull(bindIp, "bindIp must not be null");
        this.smtpSessionManager = Objects.requireNonNull(smtpSessionManager, "smtpHelper must not be null");
        this.emailMimeComposer = Objects.requireNonNull(emailMimeComposer, "emailMimeComposer must not be null");
        this.resultApplier = Objects.requireNonNull(resultApplier, "resultApplier must not be null");
        this.retryExcludeCode = retryExcludeCode != null ? retryExcludeCode : Collections.emptyList();
        this.domainSendMetrics = Objects.requireNonNull(domainSendMetrics, "domainSendMetrics must not be null");
    }

    /**
     * Callable interface implementation: Called when executed in a Thread.
     *
     * @return Number of successfully sent targets
     */
    @Override
    public Integer call() {
        return sendBatch();
    }

    /**
     * Receives batch data and sends emails.
     *
     * @return Number of successfully sent targets
     */
    private int sendBatch() {
        if (batch == null || batch.isEmpty()) {
            log.warn("Empty batch received, skipping send");
            return 0;
        }

        log.info("Starting to send batch for domain '{}' with {} targets", domain, batch.size());
        int successCount = 0;

        try {
            // 1. Pre-send preparation
            try {
                prepareSend();
            } catch (RuntimeException e) {
                // On session open failure, propagate same code to entire batch
                if (e instanceof SmtpSessionOpenException sessionOpenException) {
                    captureLastError(sessionOpenException.getStatusCode(), sessionOpenException.getOriginalMessage());
                    localBindFailure = sessionOpenException.isLocalBindError();
                } else {
                    captureLastError(600, e.getMessage());
                }
                // Mark first target and subsequent unprocessed (701) with same code
                propagateFailureToRemaining(0, lastErrorStatusCode, lastErrorMessage);
                domainSendMetrics.recordSessionFailure(domain);
                return 0;
            }

            // 2. Send email to each target
            for (int i = 0; i < batch.size(); i++) {
                EmailSendTarget emailSendTarget = batch.get(i);
                try {
                    // Send only new data (retry_count == 0) or retry targets (retry_count > 0)
                    int retryCount = emailSendTarget.retryCount();
                    if (retryCount >= 0) {
                        boolean sent = sendEmail(emailSendTarget);
                        if (sent) {
                            successCount++;
                            hasAnySuccess = true;
                        }
                        // Result reflection and recording is handled inside resultApplier.apply
                    } else {
                        // Retry count exceeded - skip
                        log.warn("Skipping target with invalid retry_count: {}", emailSendTarget.getTargetEmail());
                    }
                } catch (Exception e) {
                    if (e instanceof ResultPersistenceException) {
                        throw e;
                    }
                    log.error("Failed to send email to target: {}", emailSendTarget.getTargetEmail(), e);
                    // Handle failure
                    handleSendFailure(emailSendTarget, e);
                }

                // If session is broken, propagate same code to remaining unprocessed (701) items and break loop
                if (sessionBroken) {
                    propagateFailureToRemaining(i + 1, lastErrorStatusCode, lastErrorMessage);
                    domainSendMetrics.recordSessionFailure(domain);
                    break;
                }
            }
            log.info("Batch send completed for domain '{}'. Success: {}/{}", domain, successCount, batch.size());
        } catch (ResultPersistenceException e) {
            log.error("Batch send aborted due to result persistence failure", e);
            throw e;
        } catch (Exception e) {
            log.error("Batch send failed", e);
        } finally {
            // Ensure session cleanup in all paths including ResultPersistenceException
            finalizeSend();
        }

        return successCount;
    }

    /**
     * Performs pre-send preparation tasks.
     * Connects SMTP session with server.
     */
    private void prepareSend() {
        log.debug("Preparing to send batch for domain: {}", domain);

        try {
            smtpSessionManager.openSession(domain, bindIp);
            log.debug("SMTP session created successfully for domain: {} with bindIp={}", domain, bindIp);
        } catch (SmtpSessionOpenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create SMTP session for domain: {} with bindIp={}", domain, bindIp, e);
            throw new RuntimeException("Failed to create SMTP session", e);
        }
    }

    /**
     * Sends email to individual target.
     *
     * @param emailSendTarget Target data (includes target_email, target_id, etc.)
     * @return Whether sending succeeded
     */
    private boolean sendEmail(EmailSendTarget emailSendTarget) {
        String email = emailSendTarget.getTargetEmail();
        String message;

        log.debug("Sending email (targetId: {})", emailSendTarget.getTargetId());

        try {
            if (!smtpSessionManager.isSessionValid()) {
                log.error("SMTP Session is not valid. Domain: {}", emailSendTarget.getDomain());
                captureLastError(SmtpStatus.SESSION_INVALID, "SMTP Session is not valid");
                resultApplier.apply(emailSendTarget, SendResult.failure(SmtpStatus.SESSION_INVALID, lastErrorMessage));
                // If session is invalid, RSET is also likely to fail, so consider session broken
                sessionBroken = true;
                return false;
            }

            // 0. Content creation
            try {
                message = emailMimeComposer.makeMime(emailSendTarget);
            } catch (Exception makeEx) {
                log.error("Failed to make MIME for: {}", email, makeEx);
                captureLastError(SmtpStatus.MIME_BUILD_FAILED, makeEx.getMessage());
                resultApplier.apply(emailSendTarget, SendResult.failure(SmtpStatus.MIME_BUILD_FAILED, lastErrorMessage));
                return false;
            }

            // Start SMTP I/O time measurement (excluding MIME creation)
            long startNanos = System.nanoTime();

            // 1. Send MAIL FROM command
            SmtpCommandResponse mailFromResponse = smtpSessionManager.sendMailFrom(emailSendTarget.getSenderEmail());
            if (!mailFromResponse.isSuccess()) {
                recordResponseTime(startNanos);
                handleSmtpError(emailSendTarget, "MFRM", mailFromResponse);
                // Mark session as broken if RSET fails
                if (!sendRsetAndCheck()) {
                    sessionBroken = true;
                }
                return false;
            }

            // 2. Send RCPT TO command
            SmtpCommandResponse rcptToResponse = smtpSessionManager.sendRcptTo(email);
            if (!rcptToResponse.isSuccess()) {
                recordResponseTime(startNanos);
                handleSmtpError(emailSendTarget, "RCPT", rcptToResponse);
                if (!sendRsetAndCheck()) {
                    sessionBroken = true;
                }
                return false;
            }

            // 3. Send DATA command
            SmtpCommandResponse dataResponse = smtpSessionManager.sendData();
            if (!dataResponse.isSuccess()) {
                recordResponseTime(startNanos);
                handleSmtpError(emailSendTarget, "DATA", dataResponse);
                if (!sendRsetAndCheck()) {
                    sessionBroken = true;
                }
                return false;
            }

            // 4. Send message body
            SmtpCommandResponse messageResponse = smtpSessionManager.sendMessage(message, domain);
            recordResponseTime(startNanos);

            if (messageResponse.isSuccess()) {
                // Success
                // Reset session state even after success
                // Handle send result (success)
                resultApplier.apply(emailSendTarget, SendResult.success(messageResponse.getStatusCode()));
                sendRsetAndCheck();
                return true;
            } else {
                // Failure
                handleSmtpError(emailSendTarget, "DATA", messageResponse);
                if (!sendRsetAndCheck()) {
                    sessionBroken = true;
                }
                return false;
            }

        } catch (ResultPersistenceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during email send to: {}", email, e);
            int code = SmtpStatus.fromException(e);
            captureLastError(code, e.getMessage());
            resultApplier.apply(emailSendTarget, SendResult.failure(code, lastErrorMessage));
            if (!sendRsetAndCheck()) {
                sessionBroken = true;
            }
            return false;
        } finally {
            // Remove heavy derived fields to reduce memory footprint
            resultApplier.cleanup(emailSendTarget);
        }
    }

    /**
     * Records SMTP I/O response time.
     */
    private void recordResponseTime(long startNanos) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        domainSendMetrics.recordResponseTime(domain, elapsedMs);
    }

    /**
     * Handles SMTP error.
     *
     * @param emailSendTarget  SMTP request
     * @param step     SMTP step
     * @param response SMTP command response
     */
    private void handleSmtpError(EmailSendTarget emailSendTarget, String step, SmtpCommandResponse response) {
        captureLastError(response.getStatusCode(), response.getOriginalMessage());
        resultApplier.apply(emailSendTarget, SendResult.failure(response.getStatusCode(), response.getOriginalMessage()));
        log.warn("SMTP error at step {}: {}", step, response.getOriginalMessage());
    }

    /**
     * Sends RSET command to reset session.
     */
    private boolean sendRsetAndCheck() {
        try {
            SmtpCommandResponse rsetResponse = smtpSessionManager.sendRset();
            if (!rsetResponse.isSuccess()) {
                log.warn("RSET command failed: {}", rsetResponse.getOriginalMessage());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to send RSET command", e);
            return false;
        }
    }

    /**
     * Handles email send failure.
     *
     * @param emailSendTarget   SMTP request
     * @param exception Exception that occurred
     */
    private void handleSendFailure(EmailSendTarget emailSendTarget, Exception exception) {
        int code = SmtpStatus.fromException(exception);
        captureLastError(code, exception.getMessage());
        resultApplier.apply(emailSendTarget, SendResult.failure(code, lastErrorMessage));
        if (!sendRsetAndCheck()) {
            sessionBroken = true;
        }
        log.error("Send failure handled for target: {}", emailSendTarget.getTargetEmail(), exception);
    }

    /**
     * Stores the last error code.
     */
    private void captureLastError(int statusCode, String originalMessage) {
        this.lastErrorStatusCode = statusCode;
        // originalMessage may come without code, so add prefix code if no 3-digit code present
        if (originalMessage == null) {
            originalMessage = String.valueOf(statusCode);
        }
        if (!originalMessage.matches("^\\d{3}.*")) {
            this.lastErrorMessage = statusCode + " " + originalMessage;
        } else {
            this.lastErrorMessage = originalMessage;
        }
    }

    /**
     * Marks unprocessed (701) targets after current index with same error code.
     * Mimics original process701 behavior.
     */
    private void propagateFailureToRemaining(int startIndex, int statusCode, String originalMessage) {
        for (int i = startIndex; i < batch.size(); i++) {
            EmailSendTarget req = batch.get(i);
            String code = req.getSendCode();
            if (isUnprocessed(code)) {
                resultApplier.apply(req, SendResult.failure(statusCode, originalMessage));
            }
        }
    }

    /**
     * Checks if target's send_code is unprocessed (701).
     */
    private boolean isUnprocessed(String sendCode) {
        if (sendCode == null) {
            return true;
        }
        return "701".equals(sendCode);
    }

    /**
     * Performs post-send cleanup tasks.
     * Closes SMTP session.
     */
    private void finalizeSend() {
        log.debug("Finalizing batch send for domain: {}", domain);

        try {
            if (smtpSessionManager.isSessionValid()) {
                SmtpCommandResponse quitResponse = smtpSessionManager.sendQuit();
                if (!quitResponse.isSuccess()) {
                    log.warn("QUIT command failed before closing session: {}", quitResponse.getOriginalMessage());
                }
            }
        } catch (Exception e) {
            // QUIT failure is compensated by socket close
            log.warn("Failed to send QUIT command for domain: {}", domain, e);
        }

        try {
            smtpSessionManager.closeSession();
            log.debug("SMTP session closed successfully for domain: {}", domain);
        } catch (Exception e) {
            log.error("Failed to close SMTP session for domain: {}", domain, e);
        }
    }

    /**
     * Extracts targets not included in retry exclude codes as retry targets.
     * <p>
     * - Codes included in retryExcludeCode: do not retry (treat as completed)
     * - Other codes: retry targets
     *
     * @return Retry target list
     */
    public List<EmailSendTarget> extractRetryTargets() {
        if (localBindFailure) {
            return Collections.emptyList();
        }
        return batch.stream()
                .filter(req -> {
                    String code = req.getSendCode();
                    if (code == null) {
                        return false;
                    }
                    // If included in retry exclude codes, not a retry target
                    return !retryExcludeCode.contains(code);
                })
                .collect(Collectors.toList());
    }

    /**
     * Increments retry_count for retry targets.
     * Engine layer uses this method to update state before creating retry batch.
     *
     * @param targets Retry target list
     */
    public void incrementRetryCount(List<EmailSendTarget> targets) {
        resultApplier.incrementRetryCount(targets);
    }

    public boolean hasAnySuccess() {
        return hasAnySuccess;
    }
}
