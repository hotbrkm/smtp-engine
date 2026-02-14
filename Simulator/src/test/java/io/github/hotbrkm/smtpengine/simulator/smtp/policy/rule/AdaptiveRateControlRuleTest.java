package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyReason;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.MessageContext;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AdaptiveRateControlRule Test")
class AdaptiveRateControlRuleTest {

    @DisplayName("Excludes synthetic results from statistics when include-synthetic=false")
    @Test
    void testAfterEvaluationIgnoresSyntheticOutcomeWhenConfigured() {
        SimulatorSmtpProperties.PolicySet.AdaptiveRateControl config = configForSyntheticCheck(false);
        AdaptiveRateControlRule rule = new AdaptiveRateControlRule(config);

        PolicyContext first = context("192.168.0.10", "sender@example.com");
        rule.evaluate(first);
        rule.afterEvaluation(first, io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome
                .tempFail(451, "temp", PolicyReason.RATE_LIMIT)
                .withSynthetic(true));

        PolicyContext second = context("192.168.0.10", "sender@example.com");
        rule.evaluate(second);

        assertThat(second.getStringAttribute(AdaptiveRateControlRule.ATTR_CURRENT_TIER)).isEqualTo("NORMAL");
    }

    @DisplayName("Promotes to upper tier when soft-fail rate exceeds threshold")
    @Test
    void testEvaluatePromotesTierOnSoftFailRate() {
        SimulatorSmtpProperties.PolicySet.AdaptiveRateControl config = configForSyntheticCheck(true);
        AdaptiveRateControlRule rule = new AdaptiveRateControlRule(config);

        PolicyContext first = context("192.168.0.11", "sender@example.com");
        rule.evaluate(first);
        rule.afterEvaluation(first,
                io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome.tempFail(451, "temp", PolicyReason.RATE_LIMIT));

        PolicyContext second = context("192.168.0.11", "sender@example.com");
        rule.evaluate(second);

        assertThat(second.getStringAttribute(AdaptiveRateControlRule.ATTR_CURRENT_TIER)).isEqualTo("WARNING");
    }

    @DisplayName("Does not return to lower tier before cooldown period passes")
    @Test
    void testEvaluateHoldsDowngradeUntilCooldownElapsed() throws InterruptedException {
        SimulatorSmtpProperties.PolicySet.AdaptiveRateControl config = new SimulatorSmtpProperties.PolicySet.AdaptiveRateControl();
        config.setScope("ip+mail-from-domain");
        config.setObserveWindow(Duration.ofSeconds(3));
        config.setCooldown(Duration.ofMillis(150));
        config.setIncludeSynthetic(true);

        config.setTiers(List.of(
                tier("NORMAL", Duration.ofSeconds(1), 100, enterWhenAlways()),
                tier("LIMITED", Duration.ofSeconds(1), 100, enterWhenSoftFailRate(1.0))
        ));

        AdaptiveRateControlRule rule = new AdaptiveRateControlRule(config);

        PolicyContext first = context("192.168.0.12", "sender@example.com");
        rule.evaluate(first);
        rule.afterEvaluation(first,
                io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome.tempFail(451, "temp", PolicyReason.RATE_LIMIT));

        PolicyContext second = context("192.168.0.12", "sender@example.com");
        rule.evaluate(second);
        assertThat(second.getStringAttribute(AdaptiveRateControlRule.ATTR_CURRENT_TIER)).isEqualTo("LIMITED");

        rule.afterEvaluation(second, io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome.allow());

        PolicyContext third = context("192.168.0.12", "sender@example.com");
        rule.evaluate(third);
        assertThat(third.getStringAttribute(AdaptiveRateControlRule.ATTR_CURRENT_TIER)).isEqualTo("LIMITED");

        Thread.sleep(220L);

        PolicyContext fourth = context("192.168.0.12", "sender@example.com");
        rule.evaluate(fourth);
        assertThat(fourth.getStringAttribute(AdaptiveRateControlRule.ATTR_CURRENT_TIER)).isEqualTo("NORMAL");
    }

    @DisplayName("Returns TEMP_FAIL with configured code when current tier limit is exceeded")
    @Test
    void testEvaluateReturnsConfiguredCodeWhenTierLimitExceeded() {
        SimulatorSmtpProperties.PolicySet.AdaptiveRateControl config = new SimulatorSmtpProperties.PolicySet.AdaptiveRateControl();
        config.setScope("ip+mail-from-domain");
        config.setObserveWindow(Duration.ofMinutes(1));
        config.setCooldown(Duration.ZERO);
        config.setIncludeSynthetic(true);

        SimulatorSmtpProperties.PolicySet.Reply onLimit = new SimulatorSmtpProperties.PolicySet.Reply();
        onLimit.setCode(451);
        onLimit.setMessage("Deferred by policy.");
        config.setOnLimit(onLimit);

        config.setTiers(List.of(
                tier("NORMAL", Duration.ofSeconds(5), 1, enterWhenAlways())
        ));

        AdaptiveRateControlRule rule = new AdaptiveRateControlRule(config);

        var first = rule.evaluate(context("192.168.0.13", "sender@example.com"));
        var second = rule.evaluate(context("192.168.0.13", "sender@example.com"));

        assertThat(first.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(second.decision()).isEqualTo(PolicyDecision.TEMP_FAIL);
        assertThat(second.smtpCode()).isEqualTo(451);
    }

    private SimulatorSmtpProperties.PolicySet.AdaptiveRateControl configForSyntheticCheck(boolean includeSynthetic) {
        SimulatorSmtpProperties.PolicySet.AdaptiveRateControl config = new SimulatorSmtpProperties.PolicySet.AdaptiveRateControl();
        config.setScope("ip+mail-from-domain");
        config.setObserveWindow(Duration.ofSeconds(5));
        config.setCooldown(Duration.ofSeconds(1));
        config.setIncludeSynthetic(includeSynthetic);

        config.setTiers(List.of(
                tier("NORMAL", Duration.ofSeconds(1), 100, enterWhenAlways()),
                tier("WARNING", Duration.ofSeconds(1), 100, enterWhenSoftFailRate(0.5))
        ));
        return config;
    }

    private SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.Tier tier(
            String name,
            Duration window,
            Integer max,
            SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.EnterWhen enterWhen
    ) {
        SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.Tier tier =
                new SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.Tier();
        tier.setName(name);
        tier.setWindow(window);
        tier.setMaxRcptPerWindow(max);
        tier.setEnterWhen(enterWhen);
        return tier;
    }

    private SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.EnterWhen enterWhenAlways() {
        SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.EnterWhen when =
                new SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.EnterWhen();
        when.setAlways(true);
        return when;
    }

    private SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.EnterWhen enterWhenSoftFailRate(double value) {
        SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.EnterWhen when =
                new SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.EnterWhen();
        when.setSoftfailRateGte(value);
        return when;
    }

    private PolicyContext context(String ip, String mailFrom) {
        MessageContext messageContext = mock(MessageContext.class);
        when(messageContext.getRemoteAddress()).thenReturn(new InetSocketAddress(ip, 2525));

        return PolicyContext.builder()
                .messageContext(messageContext)
                .phase(SmtpPhase.RCPT_PRE)
                .mailFrom(mailFrom)
                .currentRecipient("user@example.net")
                .build();
    }
}
