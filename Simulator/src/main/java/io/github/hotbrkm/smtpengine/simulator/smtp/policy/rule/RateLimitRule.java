package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyReason;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.support.PolicyScopeKeyResolver;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.support.WindowCounterStore;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@RequiredArgsConstructor
public class RateLimitRule implements PolicyRule {

    private static final int DEFAULT_CODE = 451;
    private static final String DEFAULT_MESSAGE = "4.7.0 Temporarily deferred.";

    private final SimulatorSmtpProperties.PolicySet.RateLimit config;
    private final WindowCounterStore store = new WindowCounterStore();

    @Override
    public boolean supports(SmtpPhase phase) {
        return phase == SmtpPhase.RCPT_PRE;
    }

    @Override
    public PolicyOutcome evaluate(PolicyContext context) {
        if (config == null) {
            return PolicyOutcome.allow();
        }

        long windowMillis = toMillis(config.getWindow());
        int max = config.getMaxRcptPerWindow() == null ? 0 : config.getMaxRcptPerWindow();
        if (windowMillis <= 0 || max <= 0) {
            return PolicyOutcome.allow();
        }

        String key = PolicyScopeKeyResolver.resolve(config.getScope(), context);
        boolean allowed = store.tryAcquire(key, windowMillis, max);
        if (allowed) {
            return PolicyOutcome.allow();
        }

        int code = DEFAULT_CODE;
        String message = DEFAULT_MESSAGE;
        if (config.getOnExceed() != null) {
            if (config.getOnExceed().getCode() != null) {
                code = config.getOnExceed().getCode();
            }
            if (config.getOnExceed().getMessage() != null && !config.getOnExceed().getMessage().isBlank()) {
                message = config.getOnExceed().getMessage();
            }
        }

        if (code >= 500) {
            return PolicyOutcome.permFail(code, message, PolicyReason.RATE_LIMIT);
        }
        return PolicyOutcome.tempFail(code, message, PolicyReason.RATE_LIMIT);
    }

    private static long toMillis(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 0L;
        }
        return duration.toMillis();
    }
}
