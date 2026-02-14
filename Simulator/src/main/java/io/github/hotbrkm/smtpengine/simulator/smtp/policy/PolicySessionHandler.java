package io.github.hotbrkm.smtpengine.simulator.smtp.policy;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import org.subethamail.smtp.server.Session;
import org.subethamail.smtp.server.SessionHandler;

public class PolicySessionHandler implements SessionHandler {

    private final PolicyOrchestrator orchestrator;

    public PolicySessionHandler(PolicyOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public SessionAcceptance accept(Session session) {
        PolicyContext context = PolicyContext.builder()
                .messageContext(session)
                .phase(SmtpPhase.CONNECT_PRE)
                .build();

        PolicyOutcome outcome = orchestrator.evaluate(SmtpPhase.CONNECT_PRE, context);
        applyDelay(outcome);

        if (outcome.decision() == PolicyDecision.ALLOW) {
            return SessionAcceptance.success();
        }

        int code = outcome.smtpCode() > 0 ? outcome.smtpCode() : 421;
        String message = outcome.message() == null ? "4.7.0 Connection rejected by policy" : outcome.message();
        return SessionAcceptance.failure(code, message);
    }

    @Override
    public void onSessionEnd(Session session) {
        PolicyContext context = PolicyContext.builder()
                .messageContext(session)
                .phase(SmtpPhase.SESSION_END)
                .build();
        orchestrator.notifySessionEnd(context);
    }

    private void applyDelay(PolicyOutcome outcome) {
        if (outcome == null || outcome.delayMillis() <= 0) {
            return;
        }
        try {
            Thread.sleep(outcome.delayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
