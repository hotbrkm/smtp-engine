package io.github.hotbrkm.smtpengine.simulator.smtp.policy;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.server.SessionHandler;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicySessionHandler Test")
class PolicySessionHandlerTest {

    @DisplayName("Returns session acceptance when CONNECT_PRE result is ALLOW")
    @Test
    void testAcceptReturnsSuccessWhenPolicyAllows() {
        CapturingOrchestrator orchestrator = new CapturingOrchestrator();
        orchestrator.nextEvaluateOutcome = PolicyOutcome.allow();

        PolicySessionHandler handler = new PolicySessionHandler(orchestrator);

        SessionHandler.SessionAcceptance result = handler.accept(null);

        assertThat(result.accepted()).isTrue();
        assertThat(result.errorCode()).isZero();
        assertThat(result.errorMessage()).isNull();
        assertThat(orchestrator.lastEvaluatePhase).isEqualTo(SmtpPhase.CONNECT_PRE);
        assertThat(orchestrator.lastEvaluateContext.getPhase()).isEqualTo(SmtpPhase.CONNECT_PRE);
    }

    @DisplayName("Returns session rejection with policy code and message when CONNECT_PRE result is failure")
    @Test
    void testAcceptReturnsFailureWithPolicyCodeAndMessage() {
        CapturingOrchestrator orchestrator = new CapturingOrchestrator();
        orchestrator.nextEvaluateOutcome =
                PolicyOutcome.tempFail(451, "4.7.0 busy", PolicyReason.RATE_LIMIT);

        PolicySessionHandler handler = new PolicySessionHandler(orchestrator);

        SessionHandler.SessionAcceptance result = handler.accept(null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo(451);
        assertThat(result.errorMessage()).isEqualTo("4.7.0 busy");
    }

    @DisplayName("Uses default values (421/default message) when policy failure result has no code or message")
    @Test
    void testAcceptUsesDefaultFailureCodeAndMessageWhenMissing() {
        CapturingOrchestrator orchestrator = new CapturingOrchestrator();
        orchestrator.nextEvaluateOutcome = new PolicyOutcome(PolicyDecision.TEMP_FAIL, 0, null,
                PolicyReason.NONE, false, 0L, false);

        PolicySessionHandler handler = new PolicySessionHandler(orchestrator);

        SessionHandler.SessionAcceptance result = handler.accept(null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo(421);
        assertThat(result.errorMessage()).isEqualTo("4.7.0 Connection rejected by policy");
    }

    @DisplayName("Applies delay before returning accept if policy delay value exists")
    @Test
    void testAcceptAppliesDelayBeforeReturning() {
        CapturingOrchestrator orchestrator = new CapturingOrchestrator();
        orchestrator.nextEvaluateOutcome = PolicyOutcome.allow().withDelay(60L);

        PolicySessionHandler handler = new PolicySessionHandler(orchestrator);

        long startNanos = System.nanoTime();
        handler.accept(null);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        assertThat(elapsedMillis).isGreaterThanOrEqualTo(45L);
    }

    @DisplayName("Delivers to orchestrator with SESSION_END context on session end")
    @Test
    void testOnSessionEndPassesSessionEndContextToOrchestrator() {
        CapturingOrchestrator orchestrator = new CapturingOrchestrator();
        PolicySessionHandler handler = new PolicySessionHandler(orchestrator);

        handler.onSessionEnd(null);

        assertThat(orchestrator.lastSessionEndContext).isNotNull();
        assertThat(orchestrator.lastSessionEndContext.getPhase()).isEqualTo(SmtpPhase.SESSION_END);
    }

    private static final class CapturingOrchestrator extends PolicyOrchestrator {

        private PolicyOutcome nextEvaluateOutcome = PolicyOutcome.allow();
        private SmtpPhase lastEvaluatePhase;
        private PolicyContext lastEvaluateContext;
        private PolicyContext lastSessionEndContext;

        @Override
        public PolicyOutcome evaluate(SmtpPhase phase, PolicyContext context) {
            this.lastEvaluatePhase = phase;
            this.lastEvaluateContext = context;
            return nextEvaluateOutcome;
        }

        @Override
        public void notifySessionEnd(PolicyContext context) {
            this.lastSessionEndContext = context;
        }
    }
}
