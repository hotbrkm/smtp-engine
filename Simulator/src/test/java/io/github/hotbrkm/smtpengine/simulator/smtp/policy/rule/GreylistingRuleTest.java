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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GreylistingRule Test")
class GreylistingRuleTest {

    @DisplayName("First attempt returns TEMP_FAIL with template message")
    @Test
    void testEvaluateTempFailOnFirstSeenWithTemplateValues() {
        GreylistingRule rule = new GreylistingRule(config(451));

        var outcome = rule.evaluate(context("192.168.1.10", "sender@example.com", "rcpt@example.net"));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.TEMP_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(451);
        assertThat(outcome.message()).contains("4.7.1");
        assertThat(outcome.message()).contains("192.168.1.10|sender@example.com|rcpt@example.net");
    }

    @DisplayName("Allows after minimum retry delay and maintains whitelist")
    @Test
    void testEvaluateAllowAfterRetryMinDelayAndWhitelistFollowingAttempt() throws InterruptedException {
        GreylistingRule rule = new GreylistingRule(config(451));

        PolicyContext context = context("192.168.1.11", "sender@example.com", "rcpt@example.net");
        assertThat(rule.evaluate(context).decision()).isEqualTo(PolicyDecision.TEMP_FAIL);

        Thread.sleep(70L);

        assertThat(rule.evaluate(context).decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(rule.evaluate(context).decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    @DisplayName("Returns PERM_FAIL when response code is 5xx")
    @Test
    void testEvaluatePermFailWhenResponseCodeIs5xx() {
        GreylistingRule rule = new GreylistingRule(config(550));

        var outcome = rule.evaluate(context("192.168.1.12", "sender@example.com", "rcpt@example.net"));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.PERM_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(550);
    }

    @DisplayName("Allows immediately when recipient matches whitelist")
    @Test
    void testEvaluateAllowWhenRecipientIsWhitelisted() {
        SimulatorSmtpProperties.PolicySet.Greylisting config = config(451);
        SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
        bypass.setAuthenticated(false);
        bypass.setWhitelistedRecipients(List.of("rcpt@example.net"));
        config.setBypass(bypass);

        GreylistingRule rule = new GreylistingRule(config);

        var outcome = rule.evaluate(context("192.168.1.13", "sender@example.com", "rcpt@example.net"));
        assertThat(outcome.decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    private SimulatorSmtpProperties.PolicySet.Greylisting config(int responseCode) {
        SimulatorSmtpProperties.PolicySet.Greylisting config = new SimulatorSmtpProperties.PolicySet.Greylisting();
        config.setEnabled(true);
        config.setTrackBy("ip-mail-from-rcpt-to");
        config.setRetryMinDelay(Duration.ofMillis(50));
        config.setRetryMaxWindow(Duration.ofSeconds(2));
        config.setWhitelistDuration(Duration.ofSeconds(2));
        config.setPendingExpiration(Duration.ofSeconds(1));

        SimulatorSmtpProperties.PolicySet.Greylisting.Response response = new SimulatorSmtpProperties.PolicySet.Greylisting.Response();
        response.setCode(responseCode);
        response.setEnhancedStatus("4.7.1");
        response.setMessage("Greylisting in action. Try again later. [key={key}] [attempt={attemptCount}]");
        config.setResponse(response);
        return config;
    }

    private PolicyContext context(String ip, String mailFrom, String rcpt) {
        MessageContext messageContext = mock(MessageContext.class);
        when(messageContext.getRemoteAddress()).thenReturn(new InetSocketAddress(ip, 2525));

        return PolicyContext.builder()
                .messageContext(messageContext)
                .phase(SmtpPhase.RCPT_PRE)
                .mailFrom(mailFrom)
                .currentRecipient(rcpt)
                .build();
    }
}
