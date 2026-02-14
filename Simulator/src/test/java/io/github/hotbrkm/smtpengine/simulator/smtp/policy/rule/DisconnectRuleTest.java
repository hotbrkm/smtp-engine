package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.MessageContext;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DisconnectRule Test")
class DisconnectRuleTest {

    @DisplayName("Returns DISCONNECT when blocked domain matches")
    @Test
    void testEvaluateDisconnectWhenBlockedDomainMatched() {
        SimulatorSmtpProperties.PolicySet.Disconnect config = baseConfig();
        config.getConditions().setBlockedDomains(List.of("spam.com"));

        DisconnectRule rule = new DisconnectRule(config);

        var outcome = rule.evaluate(context("192.168.1.10", "user@spam.com"));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.DISCONNECT);
        assertThat(outcome.smtpCode()).isEqualTo(421);
    }

    @DisplayName("Returns TEMP_FAIL when close-after-reply=false and 4xx response")
    @Test
    void testEvaluateTempFailWhenCloseAfterReplyFalseAnd4xx() {
        SimulatorSmtpProperties.PolicySet.Disconnect config = baseConfig();
        config.getConditions().setBlockedIps(List.of("192.168.1.20"));
        config.setCloseAfterReply(false);
        config.getReply().setCode(451);

        DisconnectRule rule = new DisconnectRule(config);

        var outcome = rule.evaluate(context("192.168.1.20", "user@example.com"));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.TEMP_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(451);
    }

    @DisplayName("Returns PERM_FAIL when close-after-reply=false and 5xx response")
    @Test
    void testEvaluatePermFailWhenCloseAfterReplyFalseAnd5xx() {
        SimulatorSmtpProperties.PolicySet.Disconnect config = baseConfig();
        config.getConditions().setBlockedIps(List.of("192.168.1.21"));
        config.setCloseAfterReply(false);
        config.getReply().setCode(550);

        DisconnectRule rule = new DisconnectRule(config);

        var outcome = rule.evaluate(context("192.168.1.21", "user@example.com"));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.PERM_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(550);
    }

    @DisplayName("Returns DISCONNECT when current time is in blocked time window")
    @Test
    void testEvaluateDisconnectWhenCurrentTimeInWindow() {
        SimulatorSmtpProperties.PolicySet.Disconnect config = baseConfig();

        LocalTime now = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        SimulatorSmtpProperties.PolicySet.TimeWindow window = new SimulatorSmtpProperties.PolicySet.TimeWindow();
        window.setStart(now.minusMinutes(1).format(formatter));
        window.setEnd(now.plusMinutes(1).format(formatter));

        config.getConditions().setTimeWindows(List.of(window));

        DisconnectRule rule = new DisconnectRule(config);
        var outcome = rule.evaluate(context("192.168.1.30", "user@example.com"));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.DISCONNECT);
    }

    @DisplayName("Returns ALLOW when no condition matches")
    @Test
    void testEvaluateAllowWhenNoConditionMatched() {
        SimulatorSmtpProperties.PolicySet.Disconnect config = baseConfig();
        config.getConditions().setBlockedDomains(List.of("spam.com"));

        DisconnectRule rule = new DisconnectRule(config);

        var outcome = rule.evaluate(context("192.168.1.40", "user@example.com"));
        assertThat(outcome.decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    private SimulatorSmtpProperties.PolicySet.Disconnect baseConfig() {
        SimulatorSmtpProperties.PolicySet.Disconnect config = new SimulatorSmtpProperties.PolicySet.Disconnect();
        config.setConditions(new SimulatorSmtpProperties.PolicySet.Disconnect.DisconnectConditions());
        config.setReply(new SimulatorSmtpProperties.PolicySet.Reply());
        config.getReply().setCode(421);
        config.getReply().setMessage("4.7.0 Session closed due to policy.");
        config.setCloseAfterReply(true);
        return config;
    }

    private PolicyContext context(String ip, String rcpt) {
        MessageContext messageContext = mock(MessageContext.class);
        try {
            when(messageContext.getRemoteAddress()).thenReturn(
                    new InetSocketAddress(InetAddress.getByName(ip), 2525)
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        return PolicyContext.builder()
                .messageContext(messageContext)
                .phase(SmtpPhase.RCPT_PRE)
                .currentRecipient(rcpt)
                .build();
    }
}
