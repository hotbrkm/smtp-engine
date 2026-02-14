package io.github.hotbrkm.smtpengine.simulator.smtp.policy;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.metrics.PolicyMetricsRecorder;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.AdaptiveRateControlRule;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.ConnectionLimitRule;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.DisconnectRule;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.FaultInjectionRule;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.GreylistingRule;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.MailAuthCheckRule;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.PolicyRule;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.RateLimitRule;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.ResponseDelayRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PolicyOrchestratorFactory {

    private PolicyOrchestratorFactory() {
    }

    public static PolicyOrchestrator build(SimulatorSmtpProperties.PolicySet policySet,
                                           PolicyMetricsRecorder metricsRecorder) {
        PolicyOrchestrator orchestrator = metricsRecorder != null
                ? new PolicyOrchestrator(metricsRecorder)
                : new PolicyOrchestrator();

        if (policySet == null || !policySet.isEnabled()
                || policySet.getRules() == null || policySet.getRules().isEmpty()) {
            return orchestrator;
        }

        List<SimulatorSmtpProperties.PolicySet.Rule> enabledRules = new ArrayList<>(policySet.getRules());
        enabledRules.removeIf(rule -> rule == null || !rule.isEnabled());
        enabledRules.sort(Comparator.comparingInt(SimulatorSmtpProperties.PolicySet.Rule::getOrder));

        for (SimulatorSmtpProperties.PolicySet.Rule rule : enabledRules) {
            validateRule(rule);
            PolicyRule policyRule = createPolicyRule(rule, metricsRecorder);
            for (SmtpPhase phase : resolvePhases(rule)) {
                orchestrator.register(phase, policyRule);
            }
        }

        return orchestrator;
    }

    static void validateRule(SimulatorSmtpProperties.PolicySet.Rule rule) {
        if (rule.getType() != SimulatorSmtpProperties.PolicySet.RuleType.DISCONNECT
                || rule.getDisconnect() == null
                || rule.getDisconnect().getConditions() == null
                || rule.getDisconnect().getConditions().getTimeWindows() == null) {
            return;
        }
        for (SimulatorSmtpProperties.PolicySet.TimeWindow window : rule.getDisconnect().getConditions().getTimeWindows()) {
            if (window != null) {
                window.validate();
            }
        }
    }

    static PolicyRule createPolicyRule(SimulatorSmtpProperties.PolicySet.Rule rule,
                                       PolicyMetricsRecorder metricsRecorder) {
        return switch (rule.getType()) {
            case CONNECTION_LIMIT -> new ConnectionLimitRule(rule.getConnectionLimit());
            case RATE_LIMIT -> new RateLimitRule(rule.getRateLimit());
            case ADAPTIVE_RATE_CONTROL -> metricsRecorder != null
                    ? new AdaptiveRateControlRule(rule.getAdaptiveRateControl(), metricsRecorder)
                    : new AdaptiveRateControlRule(rule.getAdaptiveRateControl());
            case RESPONSE_DELAY -> new ResponseDelayRule(rule.getResponseDelay());
            case GREYLISTING -> new GreylistingRule(rule.getGreylisting());
            case DISCONNECT -> new DisconnectRule(rule.getDisconnect());
            case MAIL_AUTH_CHECK -> metricsRecorder != null
                    ? new MailAuthCheckRule(rule.getMailAuthCheck(), metricsRecorder)
                    : new MailAuthCheckRule(rule.getMailAuthCheck());
            case FAULT_INJECTION -> new FaultInjectionRule(rule.getFaultInjection());
        };
    }

    static Set<SmtpPhase> resolvePhases(SimulatorSmtpProperties.PolicySet.Rule rule) {
        Set<SmtpPhase> phases = new LinkedHashSet<>();
        List<String> configuredPhases = rule.getPhases();
        if (configuredPhases == null || configuredPhases.isEmpty()) {
            phases.add(defaultPhase(rule.getType()));
            return phases;
        }

        for (String configuredPhase : configuredPhases) {
            try {
                SmtpPhase phase = SmtpPhase.fromConfig(configuredPhase);
                if (phase == null) {
                    throw new IllegalArgumentException("policy.rules.phases value is empty.");
                }
                phases.add(phase);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported policy.rules.phases value: "
                        + configuredPhase + " (type=" + rule.getType() + ")", e);
            }
        }
        return phases;
    }

    static SmtpPhase defaultPhase(SimulatorSmtpProperties.PolicySet.RuleType type) {
        return switch (type) {
            case CONNECTION_LIMIT -> SmtpPhase.CONNECT_PRE;
            case RATE_LIMIT, ADAPTIVE_RATE_CONTROL, RESPONSE_DELAY, GREYLISTING, DISCONNECT, FAULT_INJECTION -> SmtpPhase.RCPT_PRE;
            case MAIL_AUTH_CHECK -> SmtpPhase.DATA_END;
        };
    }
}
