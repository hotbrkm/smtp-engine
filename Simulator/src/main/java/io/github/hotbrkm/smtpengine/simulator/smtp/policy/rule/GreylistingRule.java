package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyReason;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GreylistingRule implements PolicyRule {

    private static final int DEFAULT_CODE = 451;
    private static final String DEFAULT_MESSAGE = "4.7.1 Greylisting in action. Try again later.";

    private final SimulatorSmtpProperties.PolicySet.Greylisting config;
    private final GreylistingBypassChecker bypassChecker;
    private final GreylistingKeyBuilder keyBuilder;

    private final Map<String, GreylistingPendingEntry> pending = new ConcurrentHashMap<>();
    private final Map<String, Instant> whitelist = new ConcurrentHashMap<>();

    public GreylistingRule(SimulatorSmtpProperties.PolicySet.Greylisting config) {
        this.config = config;
        this.bypassChecker = new GreylistingBypassChecker(config == null ? null : config.getBypass());
        this.keyBuilder = new GreylistingKeyBuilder(config == null ? null : config.getTrackBy());
    }

    @Override
    public boolean supports(SmtpPhase phase) {
        return phase == SmtpPhase.RCPT_PRE;
    }

    @Override
    public PolicyOutcome evaluate(PolicyContext context) {
        if (config == null || !config.isEnabled()) {
            return PolicyOutcome.allow();
        }
        if (context.getPhase() != SmtpPhase.RCPT_PRE) {
            return PolicyOutcome.allow();
        }
        if (bypassChecker.shouldBypass(context)) {
            return PolicyOutcome.allow();
        }

        String key = keyBuilder.buildKey(context);
        if (key == null || key.isBlank()) {
            return PolicyOutcome.allow();
        }

        Instant now = Instant.now();

        Instant wlExpiry = whitelist.get(key);
        if (wlExpiry != null) {
            if (now.isBefore(wlExpiry)) {
                return PolicyOutcome.allow();
            }
            whitelist.remove(key);
        }

        Duration minDelay = orDefault(config.getRetryMinDelay(), Duration.ofMinutes(5));
        Duration maxWindow = orDefault(config.getRetryMaxWindow(), Duration.ofHours(4));
        Duration whitelistDuration = orDefault(config.getWhitelistDuration(), Duration.ofDays(36));
        Duration pendingExpiration = orDefault(config.getPendingExpiration(), Duration.ofHours(4));

        GreylistingPendingEntry entry = pending.compute(key, (k, old) -> cleanupOrInit(old, now, pendingExpiration));

        Duration sinceFirst = Duration.between(entry.getFirstSeen(), now);
        if (sinceFirst.compareTo(minDelay) < 0) {
            entry.incrementAttemptCount();
            return tempFail(buildMessage(key, entry, now.plus(minDelay.minus(sinceFirst))));
        }
        if (sinceFirst.compareTo(maxWindow) > 0) {
            entry.resetFirstSeen(now);
            entry.setAttemptCount(1);
            return tempFail(buildMessage(key, entry, now.plus(minDelay)));
        }

        pending.remove(key);
        whitelist.put(key, now.plus(whitelistDuration));
        return PolicyOutcome.allow();
    }

    private static <T> T orDefault(T value, T def) {
        return value != null ? value : def;
    }

    private GreylistingPendingEntry cleanupOrInit(GreylistingPendingEntry old, Instant now, Duration pendingExpiration) {
        if (old == null) {
            return new GreylistingPendingEntry(now, 1);
        }
        if (Duration.between(old.getFirstSeen(), now).compareTo(pendingExpiration) > 0 && old.getAttemptCount() <= 1) {
            return new GreylistingPendingEntry(now, 1);
        }
        return old;
    }

    private PolicyOutcome tempFail(String message) {
        int code = DEFAULT_CODE;
        if (config.getResponse() != null && config.getResponse().getCode() != null) {
            code = config.getResponse().getCode();
        }
        if (code >= 500) {
            return PolicyOutcome.permFail(code, message, PolicyReason.GREYLISTING);
        }
        return PolicyOutcome.tempFail(code, message, PolicyReason.GREYLISTING);
    }

    private String buildMessage(String key, GreylistingPendingEntry entry, Instant nextAllowedAt) {
        SimulatorSmtpProperties.PolicySet.Greylisting.Response response = config.getResponse();
        String template = (response != null && response.getMessage() != null && !response.getMessage().isBlank())
                ? response.getMessage()
                : DEFAULT_MESSAGE;

        String firstIso = DateTimeFormatter.ISO_INSTANT.format(entry.getFirstSeen().atOffset(ZoneOffset.UTC));
        String nextIso = DateTimeFormatter.ISO_INSTANT.format(nextAllowedAt.atOffset(ZoneOffset.UTC));

        String message = template
                .replace("{key}", key)
                .replace("{firstSeenIso}", firstIso)
                .replace("{nextAllowedAtIso}", nextIso)
                .replace("{attemptCount}", Integer.toString(entry.getAttemptCount()));

        if (response != null && response.getEnhancedStatus() != null && !response.getEnhancedStatus().isBlank()) {
            message = response.getEnhancedStatus() + " " + message;
        }
        return message;
    }
}
