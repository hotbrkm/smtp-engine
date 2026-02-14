package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResponseDelayRule Test")
class ResponseDelayRuleTest {

    @DisplayName("Applies default delay to all requests when tier filter is empty")
    @Test
    void testEvaluateAppliesDelayGloballyWhenTierFilterIsEmpty() {
        SimulatorSmtpProperties.PolicySet.ResponseDelay config = new SimulatorSmtpProperties.PolicySet.ResponseDelay();
        config.setDelay(Duration.ofMillis(20));
        config.setJitter(Duration.ZERO);

        ResponseDelayRule rule = new ResponseDelayRule(config);

        var outcome = rule.evaluate(context(SmtpPhase.RCPT_PRE, null));
        assertThat(outcome.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(outcome.delayMillis()).isEqualTo(20L);
    }

    @DisplayName("Does not apply delay when current tier does not match condition")
    @Test
    void testEvaluateSkipsDelayWhenTierDoesNotMatch() {
        SimulatorSmtpProperties.PolicySet.ResponseDelay config = new SimulatorSmtpProperties.PolicySet.ResponseDelay();
        config.setDelay(Duration.ofMillis(20));

        SimulatorSmtpProperties.PolicySet.ResponseDelay.ApplyWhen applyWhen =
                new SimulatorSmtpProperties.PolicySet.ResponseDelay.ApplyWhen();
        applyWhen.setTierIn(List.of("WARNING"));
        config.setApplyWhen(applyWhen);

        ResponseDelayRule rule = new ResponseDelayRule(config);

        var outcome = rule.evaluate(context(SmtpPhase.RCPT_PRE, "NORMAL"));
        assertThat(outcome.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(outcome.delayMillis()).isZero();
    }

    @DisplayName("Applies delay within jitter range when tier condition matches")
    @Test
    void testEvaluateAppliesDelayWhenTierMatchesCaseInsensitive() {
        SimulatorSmtpProperties.PolicySet.ResponseDelay config = new SimulatorSmtpProperties.PolicySet.ResponseDelay();
        config.setDelay(Duration.ofMillis(10));
        config.setJitter(Duration.ofMillis(15));

        SimulatorSmtpProperties.PolicySet.ResponseDelay.ApplyWhen applyWhen =
                new SimulatorSmtpProperties.PolicySet.ResponseDelay.ApplyWhen();
        applyWhen.setTierIn(List.of("warning"));
        config.setApplyWhen(applyWhen);

        ResponseDelayRule rule = new ResponseDelayRule(config);

        var outcome = rule.evaluate(context(SmtpPhase.RCPT_PRE, "WARNING"));
        assertThat(outcome.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(outcome.delayMillis()).isBetween(10L, 25L);
    }

    @DisplayName("Supported phases are only RCPT_PRE and DATA_PRE")
    @Test
    void testSupportsOnlyRcptPreAndDataPre() {
        ResponseDelayRule rule = new ResponseDelayRule(new SimulatorSmtpProperties.PolicySet.ResponseDelay());

        assertThat(rule.supports(SmtpPhase.RCPT_PRE)).isTrue();
        assertThat(rule.supports(SmtpPhase.DATA_PRE)).isTrue();
        assertThat(rule.supports(SmtpPhase.CONNECT_PRE)).isFalse();
    }

    private PolicyContext context(SmtpPhase phase, String tier) {
        PolicyContext context = PolicyContext.builder()
                .phase(phase)
                .build();
        if (tier != null) {
            context.putAttribute(AdaptiveRateControlRule.ATTR_CURRENT_TIER, tier);
        }
        return context;
    }
}
