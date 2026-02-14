package io.github.hotbrkm.smtpengine.simulator.smtp.policy;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.metrics.PolicyMetricsRecorder;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule.PolicyRule;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates policy evaluation for SMTP sessions.
 * <p>
 * The orchestrator manages a collection of policy rules and evaluates them
 * based on the current SMTP phase. It collects outcomes from all rules
 * and determines the final response.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PolicyOrchestrator orchestrator = new PolicyOrchestrator();
 * orchestrator.register(SmtpPhase.RCPT_PRE, new GreylistingRule(config));
 * orchestrator.register(SmtpPhase.RCPT_PRE, new RateLimitRule(config));
 *
 * PolicyOutcome outcome = orchestrator.evaluate(SmtpPhase.RCPT_PRE, context);
 * }</pre>
 *
 * @author hotbrkm
 * @since 1.0.0
 */
public class PolicyOrchestrator {

    private final Map<SmtpPhase, List<PolicyRule>> rulesByPhase = new EnumMap<>(SmtpPhase.class);
    private final Set<PolicyRule> allRules = new LinkedHashSet<>();
    private final PolicyMetricsRecorder metricsRecorder;

    /**
     * Creates a new PolicyOrchestrator with default metrics recorder.
     */
    public PolicyOrchestrator() {
        this(new PolicyMetricsRecorder(null));
    }

    /**
     * Creates a new PolicyOrchestrator with a custom metrics recorder.
     * @param metricsRecorder the metrics recorder to use, or null for no metrics
     */
    public PolicyOrchestrator(PolicyMetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder == null ? new PolicyMetricsRecorder(null) : metricsRecorder;
    }

    /**
     * Registers a policy rule for a specific SMTP phase.
     * @param phase the SMTP phase to register for
     * @param rule the policy rule to register
     * @throws IllegalArgumentException if phase or rule is null, or rule doesn't support the phase
     */
    public void register(SmtpPhase phase, PolicyRule rule) {
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        if (rule == null) {
            throw new IllegalArgumentException("rule must not be null");
        }
        if (!rule.supports(phase)) {
            throw new IllegalArgumentException("rule " + rule.getClass().getSimpleName() + " does not support phase " + phase);
        }
        rulesByPhase.computeIfAbsent(phase, s -> new ArrayList<>()).add(rule);
        allRules.add(rule);
    }

    /**
     * Evaluates all registered rules for the given SMTP phase.
     * @param phase the current SMTP phase
     * @param context the policy context containing session information
     * @return the final policy outcome after evaluating all rules
     */
    public PolicyOutcome evaluate(SmtpPhase phase, PolicyContext context) {
        List<PolicyRule> rules = rulesByPhase.get(phase);
        if (rules == null || rules.isEmpty()) {
            return PolicyOutcome.allow();
        }

        long maxDelayMillis = 0L;
        PolicyOutcome terminalOutcome = null;

        for (PolicyRule rule : rules) {
            PolicyOutcome outcome = rule.evaluate(context);
            if (outcome == null) {
                outcome = PolicyOutcome.allow();
            }
            metricsRecorder.recordRuleFired(phase.name(), rule.getClass().getSimpleName(), outcome);

            if (outcome.delayMillis() > maxDelayMillis) {
                maxDelayMillis = outcome.delayMillis();
            }

            if (terminalOutcome == null && outcome.decision() != PolicyDecision.ALLOW) {
                terminalOutcome = outcome;
            }
        }

        PolicyOutcome finalOutcome = (terminalOutcome == null ? PolicyOutcome.allow() : terminalOutcome)
                .withDelay(maxDelayMillis);

        for (PolicyRule rule : rules) {
            rule.afterEvaluation(context, finalOutcome);
        }
        metricsRecorder.recordFinalOutcome(phase.name(), finalOutcome);
        return finalOutcome;
    }

    /**
     * Notifies all rules that the session has ended.
     * @param context the policy context
     */
    public void notifySessionEnd(PolicyContext context) {
        for (PolicyRule rule : allRules) {
            rule.onSessionEnd(context);
        }
    }
}
