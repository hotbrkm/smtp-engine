package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.MessageContext;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RateLimitRule Test")
class RateLimitRuleTest {

    @DisplayName("Returns TEMP_FAIL when scope limit exceeded within window")
    @Test
    void testEvaluateTempFailWhenScopeLimitExceededWithinWindow() {
        SimulatorSmtpProperties.PolicySet.RateLimit config = new SimulatorSmtpProperties.PolicySet.RateLimit();
        config.setScope("ip");
        config.setWindow(Duration.ofSeconds(1));
        config.setMaxRcptPerWindow(1);

        SimulatorSmtpProperties.PolicySet.Reply onExceed = new SimulatorSmtpProperties.PolicySet.Reply();
        onExceed.setCode(421);
        onExceed.setMessage("deferred");
        config.setOnExceed(onExceed);

        RateLimitRule rule = new RateLimitRule(config);

        assertThat(rule.evaluate(context("192.168.0.1")).decision()).isEqualTo(PolicyDecision.ALLOW);

        var denied = rule.evaluate(context("192.168.0.1"));
        assertThat(denied.decision()).isEqualTo(PolicyDecision.TEMP_FAIL);
        assertThat(denied.smtpCode()).isEqualTo(421);
        assertThat(denied.message()).isEqualTo("deferred");

        assertThat(rule.evaluate(context("192.168.0.2")).decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    @DisplayName("Counter resets after window time passes and allows again")
    @Test
    void testEvaluateAllowAfterWindowElapsed() throws InterruptedException {
        SimulatorSmtpProperties.PolicySet.RateLimit config = new SimulatorSmtpProperties.PolicySet.RateLimit();
        config.setScope("ip");
        config.setWindow(Duration.ofMillis(30));
        config.setMaxRcptPerWindow(1);

        RateLimitRule rule = new RateLimitRule(config);

        assertThat(rule.evaluate(context("192.168.0.3")).decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(rule.evaluate(context("192.168.0.3")).decision()).isEqualTo(PolicyDecision.TEMP_FAIL);

        Thread.sleep(60L);

        assertThat(rule.evaluate(context("192.168.0.3")).decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    @DisplayName("Returns PERM_FAIL when exceed code is 5xx")
    @Test
    void testEvaluatePermFailWhenConfiguredCodeIs5xx() {
        SimulatorSmtpProperties.PolicySet.RateLimit config = new SimulatorSmtpProperties.PolicySet.RateLimit();
        config.setScope("ip");
        config.setWindow(Duration.ofSeconds(1));
        config.setMaxRcptPerWindow(1);

        SimulatorSmtpProperties.PolicySet.Reply onExceed = new SimulatorSmtpProperties.PolicySet.Reply();
        onExceed.setCode(550);
        onExceed.setMessage("hard fail");
        config.setOnExceed(onExceed);

        RateLimitRule rule = new RateLimitRule(config);

        assertThat(rule.evaluate(context("192.168.0.4")).decision()).isEqualTo(PolicyDecision.ALLOW);

        var denied = rule.evaluate(context("192.168.0.4"));
        assertThat(denied.decision()).isEqualTo(PolicyDecision.PERM_FAIL);
        assertThat(denied.smtpCode()).isEqualTo(550);
    }

    private PolicyContext context(String ip) {
        MessageContext messageContext = mock(MessageContext.class);
        when(messageContext.getRemoteAddress()).thenReturn(new InetSocketAddress(ip, 2525));

        return PolicyContext.builder()
                .messageContext(messageContext)
                .phase(SmtpPhase.RCPT_PRE)
                .mailFrom("sender@example.com")
                .currentRecipient("rcpt@example.net")
                .build();
    }
}
