package io.github.hotbrkm.smtpengine.simulator.config;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.metrics.PolicyMetricsRecorder;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicyOrchestrator;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.PolicyRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SmtpServerConfig policy orchestrator test")
class SmtpServerConfigPolicyOrchestratorTest {

    @DisplayName("Should register all 8 policy rules with default phase mapping")
    @Test
    void testPolicyOrchestratorRegistersAllRuleTypesWithDefaultPhases() {
        SimulatorSmtpProperties properties = new SimulatorSmtpProperties();
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        List<SimulatorSmtpProperties.PolicySet.Rule> rules = new ArrayList<>();
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.FAULT_INJECTION, 950));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.MAIL_AUTH_CHECK, 900));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.CONNECTION_LIMIT, 50));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.DISCONNECT, 500));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.RATE_LIMIT, 100));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.ADAPTIVE_RATE_CONTROL, 200));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.GREYLISTING, 400));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.RESPONSE_DELAY, 220));
        policySet.setRules(rules);

        properties.setPolicy(policySet);

        PolicyOrchestrator orchestrator = new SmtpServerConfig()
                .policyOrchestrator(properties, new PolicyMetricsRecorder(null));

        Map<SmtpPhase, List<PolicyRule>> rulesByPhase = rulesByPhase(orchestrator);

        assertThat(simpleNames(rulesByPhase.get(SmtpPhase.CONNECT_PRE)))
                .containsExactly("ConnectionLimitRule");

        assertThat(simpleNames(rulesByPhase.get(SmtpPhase.RCPT_PRE)))
                .containsExactly(
                        "RateLimitRule",
                        "AdaptiveRateControlRule",
                        "ResponseDelayRule",
                        "GreylistingRule",
                        "DisconnectRule",
                        "FaultInjectionRule"
                );

        assertThat(simpleNames(rulesByPhase.get(SmtpPhase.DATA_END)))
                .containsExactly("MailAuthCheckRule");
    }

    @DisplayName("Should register with configured phases instead of default when phases setting is present")
    @Test
    void testPolicyOrchestratorUsesConfiguredPhases() {
        SimulatorSmtpProperties properties = new SimulatorSmtpProperties();
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        SimulatorSmtpProperties.PolicySet.Rule responseDelayRule =
                rule(SimulatorSmtpProperties.PolicySet.RuleType.RESPONSE_DELAY, 100);
        responseDelayRule.setPhases(List.of("data-pre"));

        policySet.setRules(List.of(responseDelayRule));
        properties.setPolicy(policySet);

        PolicyOrchestrator orchestrator = new SmtpServerConfig()
                .policyOrchestrator(properties, new PolicyMetricsRecorder(null));

        Map<SmtpPhase, List<PolicyRule>> rulesByPhase = rulesByPhase(orchestrator);

        assertThat(simpleNames(rulesByPhase.get(SmtpPhase.DATA_PRE)))
                .containsExactly("ResponseDelayRule");
        assertThat(rulesByPhase.get(SmtpPhase.RCPT_PRE)).isNull();
    }

    @DisplayName("Should throw exception when unsupported phases value is provided")
    @Test
    void testPolicyOrchestratorThrowsWhenPhaseIsInvalid() {
        SimulatorSmtpProperties properties = new SimulatorSmtpProperties();
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        SimulatorSmtpProperties.PolicySet.Rule rule = rule(SimulatorSmtpProperties.PolicySet.RuleType.RATE_LIMIT, 100);
        rule.setPhases(List.of("not-a-phase"));
        policySet.setRules(List.of(rule));
        properties.setPolicy(policySet);

        assertThatThrownBy(() -> new SmtpServerConfig().policyOrchestrator(properties, new PolicyMetricsRecorder(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported policy.rules.phases value");
    }

    @DisplayName("Should throw validation exception when disconnect.time-windows format is invalid")
    @Test
    void testPolicyOrchestratorThrowsWhenDisconnectTimeWindowFormatIsInvalid() {
        SimulatorSmtpProperties properties = new SimulatorSmtpProperties();
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        SimulatorSmtpProperties.PolicySet.Rule disconnectRule =
                rule(SimulatorSmtpProperties.PolicySet.RuleType.DISCONNECT, 100);

        SimulatorSmtpProperties.PolicySet.TimeWindow invalidWindow = new SimulatorSmtpProperties.PolicySet.TimeWindow();
        invalidWindow.setStart("9:00");
        invalidWindow.setEnd("10:00");

        disconnectRule.getDisconnect().getConditions().setTimeWindows(List.of(invalidWindow));

        policySet.setRules(List.of(disconnectRule));
        properties.setPolicy(policySet);

        assertThatThrownBy(() -> new SmtpServerConfig().policyOrchestrator(properties, new PolicyMetricsRecorder(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid format for time-window.start");
    }

    private SimulatorSmtpProperties.PolicySet.Rule rule(SimulatorSmtpProperties.PolicySet.RuleType type, int order) {
        SimulatorSmtpProperties.PolicySet.Rule rule = new SimulatorSmtpProperties.PolicySet.Rule();
        rule.setEnabled(true);
        rule.setType(type);
        rule.setOrder(order);
        return rule;
    }

    private Map<SmtpPhase, List<PolicyRule>> rulesByPhase(PolicyOrchestrator orchestrator) {
        try {
            Field field = PolicyOrchestrator.class.getDeclaredField("rulesByPhase");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SmtpPhase, List<PolicyRule>> value = (Map<SmtpPhase, List<PolicyRule>>) field.get(orchestrator);
            return new EnumMap<>(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> simpleNames(List<PolicyRule> rules) {
        if (rules == null) {
            return List.of();
        }
        return rules.stream()
                .map(rule -> rule.getClass().getSimpleName())
                .toList();
    }
}
