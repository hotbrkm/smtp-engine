package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyReason;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
public class ResponseDelayRule implements PolicyRule {

    private final SimulatorSmtpProperties.PolicySet.ResponseDelay config;

    @Override
    public boolean supports(SmtpPhase phase) {
        return phase == SmtpPhase.RCPT_PRE || phase == SmtpPhase.DATA_PRE;
    }

    @Override
    public PolicyOutcome evaluate(PolicyContext context) {
        if (config == null) {
            return PolicyOutcome.allow();
        }

        if (!matchesTier(context)) {
            return PolicyOutcome.allow();
        }

        long baseDelay = toMillis(config.getDelay());
        long jitter = toMillis(config.getJitter());
        long delayMillis = baseDelay;

        if (jitter > 0) {
            delayMillis += ThreadLocalRandom.current().nextLong(jitter + 1);
        }

        if (delayMillis <= 0) {
            return PolicyOutcome.allow();
        }
        return PolicyOutcome.delay(delayMillis, PolicyReason.RESPONSE_DELAY);
    }

    private boolean matchesTier(PolicyContext context) {
        List<String> tiers = config.getApplyWhen() == null ? null : config.getApplyWhen().getTierIn();
        if (tiers == null || tiers.isEmpty()) {
            return true;
        }

        String currentTier = context.getStringAttribute(AdaptiveRateControlRule.ATTR_CURRENT_TIER);
        if (currentTier == null || currentTier.isBlank()) {
            return false;
        }

        String normalized = currentTier.trim().toUpperCase(Locale.ROOT);
        for (String tier : tiers) {
            if (tier != null && normalized.equals(tier.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static long toMillis(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 0L;
        }
        return duration.toMillis();
    }
}
