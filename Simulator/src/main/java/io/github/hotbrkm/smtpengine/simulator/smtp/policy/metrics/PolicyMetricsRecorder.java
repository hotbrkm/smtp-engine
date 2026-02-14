package io.github.hotbrkm.smtpengine.simulator.smtp.policy.metrics;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class PolicyMetricsRecorder {

    private final MeterRegistry registry;

    public PolicyMetricsRecorder(MeterRegistry registry) {
        this.registry = registry == null ? new SimpleMeterRegistry() : registry;
    }

    public void recordRuleFired(String phase, String ruleType, PolicyOutcome outcome) {
        if (outcome == null) {
            return;
        }
        if (outcome.decision() == PolicyDecision.ALLOW && outcome.delayMillis() <= 0) {
            return;
        }

        registry.counter("simulator.smtp.policy.rule.fired",
                "phase", safe(phase),
                "rule", safe(ruleType),
                "decision", outcome.decision().name(),
                "reason", outcome.reason().name())
                .increment();
    }

    public void recordFinalOutcome(String phase, PolicyOutcome outcome) {
        if (outcome == null) {
            return;
        }

        registry.counter("simulator.smtp.policy.outcome.total",
                "phase", safe(phase),
                "decision", outcome.decision().name(),
                "reason", outcome.reason().name())
                .increment();

        if (outcome.smtpCode() >= 400) {
            String family = outcome.smtpCode() < 500 ? "4xx" : "5xx";
            registry.counter("simulator.smtp.policy.smtp.code",
                    "phase", safe(phase),
                    "family", family,
                    "code", Integer.toString(outcome.smtpCode()))
                    .increment();
        }

        if (outcome.decision() == PolicyDecision.DISCONNECT) {
            registry.counter("simulator.smtp.policy.disconnect.total",
                    "phase", safe(phase),
                    "reason", outcome.reason().name())
                    .increment();
        }

        if (outcome.delayMillis() > 0) {
            registry.summary("simulator.smtp.policy.delay.millis",
                    "phase", safe(phase))
                    .record(outcome.delayMillis());
        }
    }

    public void recordTierTransition(String fromTier, String toTier) {
        registry.counter("simulator.smtp.policy.adaptive.tier.transition",
                "from", safe(fromTier),
                "to", safe(toTier))
                .increment();
    }

    public void recordAuthResult(String protocol, String result) {
        registry.counter("simulator.smtp.policy.auth.result",
                "protocol", safe(protocol),
                "result", safe(result))
                .increment();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
