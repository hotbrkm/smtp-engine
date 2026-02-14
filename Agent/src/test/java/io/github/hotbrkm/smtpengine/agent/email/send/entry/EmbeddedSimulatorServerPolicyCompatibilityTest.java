package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.simulator.config.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicyOrchestrator;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicyOrchestratorFactory;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.PolicyRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmbeddedSimulatorServer policy assembly consistency test")
class EmbeddedSimulatorServerPolicyCompatibilityTest {

    @DisplayName("PolicyOrchestratorFactory build result should register all rule types at correct phases")
    @Test
    void testBuildPolicyOrchestratorRegistersAllRuleTypes() {
        SimulatorSmtpProperties.PolicySet policySet = policySetWithAllRuleTypes();

        PolicyOrchestrator withMetrics = PolicyOrchestratorFactory.build(policySet, null);
        PolicyOrchestrator withoutMetrics = PolicyOrchestratorFactory.build(policySet, null);

        assertThat(ruleNamesByPhase(withMetrics)).isEqualTo(ruleNamesByPhase(withoutMetrics));
    }

    @DisplayName("Should throw exception when unsupported phase value is present")
    @Test
    void testBuildPolicyOrchestratorThrowsWhenPhaseIsInvalid() {
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        SimulatorSmtpProperties.PolicySet.Rule rule = rule(SimulatorSmtpProperties.PolicySet.RuleType.RATE_LIMIT, 100);
        rule.setPhases(List.of("not-a-phase"));
        policySet.setRules(List.of(rule));

        assertThatThrownBy(() -> PolicyOrchestratorFactory.build(policySet, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported policy.rules.phases value");
    }

    @DisplayName("Should throw exception when disconnect time-window format is invalid")
    @Test
    void testBuildPolicyOrchestratorThrowsWhenDisconnectTimeWindowFormatIsInvalid() {
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        SimulatorSmtpProperties.PolicySet.Rule disconnect =
                rule(SimulatorSmtpProperties.PolicySet.RuleType.DISCONNECT, 100);
        SimulatorSmtpProperties.PolicySet.TimeWindow window = new SimulatorSmtpProperties.PolicySet.TimeWindow();
        window.setStart("9:00");
        window.setEnd("10:00");
        disconnect.getDisconnect().getConditions().setTimeWindows(List.of(window));

        policySet.setRules(List.of(disconnect));

        assertThatThrownBy(() -> PolicyOrchestratorFactory.build(policySet, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid format for time-window.start");
    }

    private SimulatorSmtpProperties.PolicySet policySetWithAllRuleTypes() {
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        List<SimulatorSmtpProperties.PolicySet.Rule> rules = new ArrayList<>();
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.FAULT_INJECTION, 950));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.MAIL_AUTH_CHECK, 900));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.CONNECTION_LIMIT, 50));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.DISCONNECT, 500));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.RATE_LIMIT, 100));
        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.ADAPTIVE_RATE_CONTROL, 200));

        SimulatorSmtpProperties.PolicySet.Rule responseDelay =
                rule(SimulatorSmtpProperties.PolicySet.RuleType.RESPONSE_DELAY, 220);
        responseDelay.setPhases(List.of("data-pre", "rcpt-pre"));
        rules.add(responseDelay);

        rules.add(rule(SimulatorSmtpProperties.PolicySet.RuleType.GREYLISTING, 400));

        SimulatorSmtpProperties.PolicySet.Rule disabled =
                rule(SimulatorSmtpProperties.PolicySet.RuleType.RATE_LIMIT, 10);
        disabled.setEnabled(false);
        rules.add(disabled);

        rules.sort(Comparator.comparingInt(SimulatorSmtpProperties.PolicySet.Rule::getOrder).reversed());
        policySet.setRules(rules);
        return policySet;
    }

    private SimulatorSmtpProperties.PolicySet.Rule rule(SimulatorSmtpProperties.PolicySet.RuleType type, int order) {
        SimulatorSmtpProperties.PolicySet.Rule rule = new SimulatorSmtpProperties.PolicySet.Rule();
        rule.setEnabled(true);
        rule.setType(type);
        rule.setOrder(order);
        if (type == SimulatorSmtpProperties.PolicySet.RuleType.DISCONNECT) {
            SimulatorSmtpProperties.PolicySet.TimeWindow valid = new SimulatorSmtpProperties.PolicySet.TimeWindow();
            valid.setStart("00:00");
            valid.setEnd("23:59");
            rule.getDisconnect().getConditions().setTimeWindows(List.of(valid));
        }
        return rule;
    }

    private Map<SmtpPhase, List<String>> ruleNamesByPhase(PolicyOrchestrator orchestrator) {
        Map<SmtpPhase, List<PolicyRule>> byPhase = readRulesByPhase(orchestrator);
        Map<SmtpPhase, List<String>> names = new EnumMap<>(SmtpPhase.class);
        for (Map.Entry<SmtpPhase, List<PolicyRule>> entry : byPhase.entrySet()) {
            names.put(entry.getKey(), entry.getValue().stream()
                    .map(rule -> rule.getClass().getSimpleName())
                    .toList());
        }
        return names;
    }

    private Map<SmtpPhase, List<PolicyRule>> readRulesByPhase(PolicyOrchestrator orchestrator) {
        try {
            Field field = PolicyOrchestrator.class.getDeclaredField("rulesByPhase");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SmtpPhase, List<PolicyRule>> value = (Map<SmtpPhase, List<PolicyRule>>) field.get(orchestrator);
            Map<SmtpPhase, List<PolicyRule>> copied = new EnumMap<>(SmtpPhase.class);
            for (Map.Entry<SmtpPhase, List<PolicyRule>> entry : value.entrySet()) {
                copied.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return copied;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
