package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.MessageContext;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ConnectionLimitRule Test")
class ConnectionLimitRuleTest {

    @DisplayName("Returns connection termination when per-IP concurrent connection limit exceeded")
    @Test
    void testEvaluateDisconnectWhenPerIpExceeded() {
        SimulatorSmtpProperties.PolicySet.ConnectionLimit config = new SimulatorSmtpProperties.PolicySet.ConnectionLimit();
        config.setPerIpMaxConnections(1);

        ConnectionLimitRule rule = new ConnectionLimitRule(config);

        PolicyContext first = context("s1", "192.168.10.10");
        PolicyContext second = context("s2", "192.168.10.10");

        assertThat(rule.evaluate(first).decision()).isEqualTo(PolicyDecision.ALLOW);

        assertThat(rule.evaluate(second).decision()).isEqualTo(PolicyDecision.DISCONNECT);
        assertThat(rule.evaluate(second).smtpCode()).isEqualTo(421);
    }

    @DisplayName("Returns PERM_FAIL when global concurrent connection limit exceeded + disconnect=false + 5xx configured")
    @Test
    void testEvaluatePermFailWhenGlobalLimitExceededAndDisconnectDisabled() {
        SimulatorSmtpProperties.PolicySet.ConnectionLimit config = new SimulatorSmtpProperties.PolicySet.ConnectionLimit();
        config.setGlobalMaxConnections(1);

        SimulatorSmtpProperties.PolicySet.Reply onExceed = new SimulatorSmtpProperties.PolicySet.Reply();
        onExceed.setDisconnect(false);
        onExceed.setCode(550);
        onExceed.setMessage("Too many global sessions");
        config.setOnExceed(onExceed);

        ConnectionLimitRule rule = new ConnectionLimitRule(config);

        assertThat(rule.evaluate(context("s1", "192.168.10.10")).decision()).isEqualTo(PolicyDecision.ALLOW);

        assertThat(rule.evaluate(context("s2", "192.168.10.11")).decision()).isEqualTo(PolicyDecision.PERM_FAIL);
        assertThat(rule.evaluate(context("s3", "192.168.10.12")).smtpCode()).isEqualTo(550);
    }

    @DisplayName("Releases connection count on session end to allow next connection")
    @Test
    void testOnSessionEndReleasesConnectionCount() {
        SimulatorSmtpProperties.PolicySet.ConnectionLimit config = new SimulatorSmtpProperties.PolicySet.ConnectionLimit();
        config.setPerIpMaxConnections(1);

        ConnectionLimitRule rule = new ConnectionLimitRule(config);

        PolicyContext first = context("s1", "192.168.10.10");
        PolicyContext second = context("s2", "192.168.10.10");

        assertThat(rule.evaluate(first).decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(rule.evaluate(second).decision()).isEqualTo(PolicyDecision.DISCONNECT);

        rule.onSessionEnd(first);

        assertThat(rule.evaluate(second).decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    private PolicyContext context(String sessionId, String ip) {
        MessageContext messageContext = mock(MessageContext.class);
        when(messageContext.getSessionId()).thenReturn(sessionId);
        when(messageContext.getRemoteAddress()).thenReturn(new InetSocketAddress(ip, 2525));

        return PolicyContext.builder()
                .messageContext(messageContext)
                .phase(SmtpPhase.CONNECT_PRE)
                .build();
    }
}
