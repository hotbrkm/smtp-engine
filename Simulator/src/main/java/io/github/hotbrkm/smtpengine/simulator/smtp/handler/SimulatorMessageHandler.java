package io.github.hotbrkm.smtpengine.simulator.smtp.handler;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.exception.DisconnectException;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicyOrchestrator;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.MailAuthCheckRule;
import io.github.hotbrkm.smtpengine.simulator.smtp.service.SmtpMessageStore;
import lombok.extern.slf4j.Slf4j;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Handles SMTP message processing for the simulator.
 * <p>
 * This handler processes SMTP commands (MAIL FROM, RCPT TO, DATA) and
 * applies policy rules at each phase. It also handles message storage
 * and authentication results attachment.
 * </p>
 *
 * @author hotbrkm
 * @since 1.0.0
 */
@Slf4j
public class SimulatorMessageHandler implements MessageHandler {

    private final MessageContext context;
    private final SimulatorSmtpProperties properties;
    private final SmtpMessageStore messageStore;
    private final PolicyOrchestrator policyOrchestrator;
    private final SmtpSessionState sessionState;

    public SimulatorMessageHandler(MessageContext context,
                                   SimulatorSmtpProperties properties,
                                   SmtpMessageStore messageStore,
                                   PolicyOrchestrator policyOrchestrator) {
        this.context = context;
        this.properties = properties;
        this.messageStore = messageStore;
        this.policyOrchestrator = policyOrchestrator;
        this.sessionState = new SmtpSessionState();
    }

    @Override
    public void from(String from) {
        sessionState.setFrom(from);
        log.debug("SMTP session started - remote={}, from={}", context.getRemoteAddress(), from);
    }

    @Override
    public void recipient(String recipient) throws RejectException {
        log.debug("Processing SMTP recipient - from={}, recipient={}", sessionState.getFrom(), recipient);

        PolicyEvaluation evaluation = evaluatePolicies(SmtpPhase.RCPT_PRE, recipient, null);
        handlePolicyOutcome(evaluation.outcome());

        sessionState.addRecipient(recipient);
    }

    @Override
    public String data(InputStream data) throws RejectException {
        log.debug("Processing SMTP DATA - from={}, recipients={}", sessionState.getFrom(), sessionState.getAcceptedRecipientCount());

        try {
            byte[] payload = data.readAllBytes();

            PolicyEvaluation dataPre = evaluatePolicies(SmtpPhase.DATA_PRE, null, payload);
            handlePolicyOutcome(dataPre.outcome());

            PolicyEvaluation dataEnd = evaluatePolicies(SmtpPhase.DATA_END, null, payload);
            handlePolicyOutcome(dataEnd.outcome());

            if (!properties.isStoreMessages() || !sessionState.hasAcceptedRecipients()) {
                return "OK";
            }

            byte[] payloadToStore = maybeAttachAuthenticationResults(payload, dataEnd.context());

            for (String recipient : sessionState.getAcceptedRecipients()) {
                try (InputStream payloadStream = new ByteArrayInputStream(payloadToStore)) {
                    messageStore.store(sessionState.getFrom(), recipient, payloadStream);
                }
            }
            return "OK";
        } catch (IOException e) {
            throw new UncheckedIOException("I/O error occurred while processing SMTP data.", e);
        }
    }

    @Override
    public void done() {
        try {
            PolicyEvaluation evaluation = evaluatePolicies(SmtpPhase.SESSION_END, null, null);
            handlePolicyOutcome(evaluation.outcome());
        } catch (RejectException e) {
            log.warn("Exception occurred during SESSION_END policy evaluation. remote={}, message={}",
                    context.getRemoteAddress(), e.getMessage());
        }
        log.debug("SMTP session ended - from={}, successful recipients={}", sessionState.getFrom(), sessionState.getAcceptedRecipientCount());
    }

    private PolicyEvaluation evaluatePolicies(SmtpPhase phase,
                                              String currentRecipient,
                                              byte[] rawMessageBytes) {
        PolicyContext policyContext = PolicyContext.builder()
                .messageContext(context)
                .mailFrom(sessionState.getFrom())
                .currentRecipient(currentRecipient)
                .sessionRcptCount(sessionState.getAcceptedRecipientCount())
                .totalRcptCount(sessionState.getAcceptedRecipientCount())
                .phase(phase)
                .rawMessageBytes(rawMessageBytes)
                .build();

        PolicyOutcome outcome = policyOrchestrator.evaluate(phase, policyContext);
        logDecision(phase, policyContext, outcome);
        return new PolicyEvaluation(policyContext, outcome);
    }

    private byte[] maybeAttachAuthenticationResults(byte[] payload, PolicyContext policyContext) {
        if (payload == null || payload.length == 0 || policyContext == null) {
            return payload;
        }
        String authResults = policyContext.getStringAttribute(MailAuthCheckRule.ATTR_AUTH_RESULTS_HEADER);
        if (authResults == null || authResults.isBlank()) {
            return payload;
        }

        String headerLine = "Authentication-Results: " + authResults + "\r\n";
        byte[] headerBytes = headerLine.getBytes(StandardCharsets.ISO_8859_1);
        byte[] result = new byte[headerBytes.length + payload.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(payload, 0, result, headerBytes.length, payload.length);
        return result;
    }

    private void handlePolicyOutcome(PolicyOutcome outcome) throws RejectException {
        if (outcome == null) {
            return;
        }

        applyDelay(outcome.delayMillis());

        if (outcome.decision() == PolicyDecision.ALLOW) {
            return;
        }

        if (outcome.decision() == PolicyDecision.DISCONNECT) {
            throw new DisconnectException(outcome.smtpCode(), outcome.message(), outcome.closeConnection());
        }

        RejectException exception = outcome.toRejectException();
        if (exception != null) {
            throw exception;
        }
    }

    private void applyDelay(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logDecision(SmtpPhase phase, PolicyContext policyContext, PolicyOutcome outcome) {
        if (outcome == null || outcome.decision() == PolicyDecision.ALLOW) {
            return;
        }

        log.info("policy-decision sessionId={} phase={} reason={} decision={} code={} msg={} remote={} mailFrom={} rcpt={}",
                context.getSessionId(),
                phase,
                outcome.reason(),
                outcome.decision(),
                outcome.smtpCode(),
                outcome.message(),
                context.getRemoteAddress(),
                policyContext.getMailFrom(),
                policyContext.getCurrentRecipient());
    }

    private record PolicyEvaluation(PolicyContext context, PolicyOutcome outcome) {
    }
}
