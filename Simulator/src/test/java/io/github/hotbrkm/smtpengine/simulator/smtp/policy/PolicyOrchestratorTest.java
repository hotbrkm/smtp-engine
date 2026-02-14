package io.github.hotbrkm.smtpengine.simulator.smtp.policy;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.*;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.metrics.PolicyMetricsRecorder;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.PolicyRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyOrchestrator Test")
class PolicyOrchestratorTest {

    @DisplayName("Applies max delay from multiple rules and keeps first terminal result")
    @Test
    void testEvaluateUsesFirstTerminalOutcomeAndMaxDelay() {
        PolicyOrchestrator orchestrator = new PolicyOrchestrator(new PolicyMetricsRecorder(null));

        StubRule allowWithDelay = new StubRule(
                EnumSet.of(SmtpPhase.RCPT_PRE),
                PolicyOutcome.delay(50L, PolicyReason.RESPONSE_DELAY)
        );
        StubRule firstTerminal = new StubRule(
                EnumSet.of(SmtpPhase.RCPT_PRE),
                PolicyOutcome.tempFail(451, "first", PolicyReason.RATE_LIMIT).withDelay(10L)
        );
        StubRule laterTerminal = new StubRule(
                EnumSet.of(SmtpPhase.RCPT_PRE),
                PolicyOutcome.permFail(550, "later", PolicyReason.FAULT_INJECTION).withDelay(120L)
        );

        orchestrator.register(SmtpPhase.RCPT_PRE, allowWithDelay);
        orchestrator.register(SmtpPhase.RCPT_PRE, firstTerminal);
        orchestrator.register(SmtpPhase.RCPT_PRE, laterTerminal);

        PolicyOutcome outcome = orchestrator.evaluate(SmtpPhase.RCPT_PRE, context(SmtpPhase.RCPT_PRE));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.TEMP_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(451);
        assertThat(outcome.message()).isEqualTo("first");
        assertThat(outcome.reason()).isEqualTo(PolicyReason.RATE_LIMIT);
        assertThat(outcome.delayMillis()).isEqualTo(120L);
    }

    @DisplayName("Calls afterEvaluation for all rules with final result after evaluation")
    @Test
    void testEvaluateInvokesAfterEvaluationForAllRulesWithFinalOutcome() {
        PolicyOrchestrator orchestrator = new PolicyOrchestrator(new PolicyMetricsRecorder(null));

        StubRule first = new StubRule(
                EnumSet.of(SmtpPhase.RCPT_PRE),
                PolicyOutcome.delay(30L, PolicyReason.RESPONSE_DELAY)
        );
        StubRule second = new StubRule(
                EnumSet.of(SmtpPhase.RCPT_PRE),
                PolicyOutcome.tempFail(421, "deferred", PolicyReason.ADAPTIVE_RATE_CONTROL)
        );
        StubRule third = new StubRule(
                EnumSet.of(SmtpPhase.RCPT_PRE),
                PolicyOutcome.allow()
        );

        orchestrator.register(SmtpPhase.RCPT_PRE, first);
        orchestrator.register(SmtpPhase.RCPT_PRE, second);
        orchestrator.register(SmtpPhase.RCPT_PRE, third);

        PolicyOutcome finalOutcome = orchestrator.evaluate(SmtpPhase.RCPT_PRE, context(SmtpPhase.RCPT_PRE));

        assertThat(first.afterEvaluationCount).isEqualTo(1);
        assertThat(second.afterEvaluationCount).isEqualTo(1);
        assertThat(third.afterEvaluationCount).isEqualTo(1);

        assertThat(first.lastAfterEvaluationOutcome).isEqualTo(finalOutcome);
        assertThat(second.lastAfterEvaluationOutcome).isEqualTo(finalOutcome);
        assertThat(third.lastAfterEvaluationOutcome).isEqualTo(finalOutcome);
    }

    @DisplayName("notifySessionEnd delivers session end context to each registered rule")
    @Test
    void testNotifySessionEndInvokesOnSessionEndForRegisteredRules() {
        PolicyOrchestrator orchestrator = new PolicyOrchestrator(new PolicyMetricsRecorder(null));

        StubRule sharedRule = new StubRule(
                EnumSet.of(SmtpPhase.CONNECT_PRE, SmtpPhase.RCPT_PRE),
                PolicyOutcome.allow()
        );

        orchestrator.register(SmtpPhase.CONNECT_PRE, sharedRule);
        orchestrator.register(SmtpPhase.RCPT_PRE, sharedRule);

        PolicyContext sessionEndContext = context(SmtpPhase.SESSION_END);
        orchestrator.notifySessionEnd(sessionEndContext);

        assertThat(sharedRule.onSessionEndCount).isEqualTo(1);
        assertThat(sharedRule.lastSessionEndContext).isEqualTo(sessionEndContext);
    }

    private PolicyContext context(SmtpPhase phase) {
        return PolicyContext.builder()
                .phase(phase)
                .build();
    }

    private static final class StubRule implements PolicyRule {

        private final Set<SmtpPhase> supportedPhases;
        private final PolicyOutcome evaluateOutcome;

        private int afterEvaluationCount;
        private int onSessionEndCount;
        private PolicyOutcome lastAfterEvaluationOutcome;
        private PolicyContext lastSessionEndContext;

        private StubRule(Set<SmtpPhase> supportedPhases, PolicyOutcome evaluateOutcome) {
            this.supportedPhases = supportedPhases;
            this.evaluateOutcome = evaluateOutcome;
        }

        @Override
        public boolean supports(SmtpPhase phase) {
            return supportedPhases.contains(phase);
        }

        @Override
        public PolicyOutcome evaluate(PolicyContext context) {
            return evaluateOutcome;
        }

        @Override
        public void afterEvaluation(PolicyContext context, PolicyOutcome finalOutcome) {
            afterEvaluationCount++;
            lastAfterEvaluationOutcome = finalOutcome;
        }

        @Override
        public void onSessionEnd(PolicyContext context) {
            onSessionEndCount++;
            lastSessionEndContext = context;
        }
    }
}
