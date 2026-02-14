package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.metrics.PolicyMetricsRecorder;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyReason;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.support.PolicyScopeKeyResolver;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.support.WindowCounterStore;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class AdaptiveRateControlRule implements PolicyRule {

    public static final String ATTR_CURRENT_TIER = "adaptive.current-tier";

    private static final int DEFAULT_LIMIT_CODE = 451;
    private static final String DEFAULT_LIMIT_MESSAGE = "Deferred by policy.";

    private final SimulatorSmtpProperties.PolicySet.AdaptiveRateControl config;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final WindowCounterStore limiter = new WindowCounterStore();
    private final PolicyMetricsRecorder metricsRecorder;

    public AdaptiveRateControlRule(SimulatorSmtpProperties.PolicySet.AdaptiveRateControl config) {
        this(config, new PolicyMetricsRecorder(null));
    }

    @Override
    public boolean supports(SmtpPhase phase) {
        return phase == SmtpPhase.RCPT_PRE;
    }

    @Override
    public PolicyOutcome evaluate(PolicyContext context) {
        if (config == null || config.getTiers() == null || config.getTiers().isEmpty()) {
            return PolicyOutcome.allow();
        }

        String key = PolicyScopeKeyResolver.resolve(config.getScope(), context);
        long now = System.currentTimeMillis();

        State state = states.computeIfAbsent(key, ignored -> new State());
        String currentTierName;

        synchronized (state) {
            prune(state, now);
            Stats stats = collectStats(state, now);
            String desiredTierName = resolveDesiredTier(stats);
            currentTierName = applyCooldown(state, desiredTierName, now);
            if (currentTierName == null || currentTierName.isBlank()) {
                currentTierName = config.getTiers().get(0).getName();
                state.currentTierName = currentTierName;
            }
        }

        context.putAttribute(ATTR_CURRENT_TIER, currentTierName);

        SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.Tier tierConfig = findTier(currentTierName);
        if (tierConfig == null) {
            return PolicyOutcome.allow();
        }

        long windowMillis = toMillis(tierConfig.getWindow());
        int max = tierConfig.getMaxRcptPerWindow() == null ? 0 : tierConfig.getMaxRcptPerWindow();
        if (windowMillis <= 0 || max <= 0) {
            return PolicyOutcome.allow();
        }

        boolean allowed = limiter.tryAcquire(key + "|" + normalize(currentTierName), windowMillis, max);
        if (allowed) {
            return PolicyOutcome.allow();
        }

        int code = DEFAULT_LIMIT_CODE;
        String message = DEFAULT_LIMIT_MESSAGE;
        if (config.getOnLimit() != null) {
            if (config.getOnLimit().getCode() != null) {
                code = config.getOnLimit().getCode();
            }
            if (config.getOnLimit().getMessage() != null && !config.getOnLimit().getMessage().isBlank()) {
                message = config.getOnLimit().getMessage();
            }
        }

        if (code >= 500) {
            return PolicyOutcome.permFail(code, message, PolicyReason.ADAPTIVE_RATE_CONTROL);
        }
        return PolicyOutcome.tempFail(code, message, PolicyReason.ADAPTIVE_RATE_CONTROL);
    }

    @Override
    public void afterEvaluation(PolicyContext context, PolicyOutcome finalOutcome) {
        if (context == null || context.getPhase() != SmtpPhase.RCPT_PRE) {
            return;
        }
        if (finalOutcome == null) {
            return;
        }
        if (finalOutcome.synthetic() && !config.isIncludeSynthetic()) {
            return;
        }

        String key = PolicyScopeKeyResolver.resolve(config.getScope(), context);
        long now = System.currentTimeMillis();

        State state = states.computeIfAbsent(key, ignored -> new State());
        synchronized (state) {
            EventType eventType = toEventType(finalOutcome);
            state.events.addLast(new Event(now, eventType));
            if (eventType != EventType.ALLOW) {
                state.lastFailureMillis = now;
            }
            prune(state, now);
        }
    }

    private EventType toEventType(PolicyOutcome outcome) {
        if (outcome.decision() == PolicyDecision.DISCONNECT) {
            return EventType.DISCONNECT;
        }
        if (outcome.decision() == PolicyDecision.PERM_FAIL || outcome.smtpCode() >= 500) {
            return EventType.HARDFAIL;
        }
        if (outcome.decision() == PolicyDecision.TEMP_FAIL || (outcome.smtpCode() >= 400 && outcome.smtpCode() < 500)) {
            return EventType.SOFTFAIL;
        }
        return EventType.ALLOW;
    }

    private void prune(State state, long now) {
        long observeWindowMillis = toMillis(config.getObserveWindow());
        if (observeWindowMillis <= 0) {
            observeWindowMillis = Duration.ofMinutes(5).toMillis();
        }
        long threshold = now - observeWindowMillis;
        while (!state.events.isEmpty() && state.events.peekFirst().timeMillis < threshold) {
            state.events.removeFirst();
        }
    }

    private Stats collectStats(State state, long now) {
        Stats stats = new Stats();
        long observeWindowMillis = toMillis(config.getObserveWindow());
        if (observeWindowMillis <= 0) {
            observeWindowMillis = Duration.ofMinutes(5).toMillis();
        }
        long threshold = now - observeWindowMillis;

        for (Event event : state.events) {
            if (event.timeMillis < threshold) {
                continue;
            }
            stats.total++;
            switch (event.type) {
                case SOFTFAIL -> stats.softfail++;
                case HARDFAIL -> stats.hardfail++;
                case DISCONNECT -> stats.disconnect++;
                case ALLOW -> {
                }
            }
        }

        int consecutive = 0;
        Event[] array = state.events.toArray(new Event[0]);
        for (int i = array.length - 1; i >= 0; i--) {
            Event event = array[i];
            if (event.timeMillis < threshold) {
                break;
            }
            if (event.type == EventType.SOFTFAIL) {
                consecutive++;
                continue;
            }
            break;
        }
        stats.softfailConsecutive = consecutive;
        return stats;
    }

    private String resolveDesiredTier(Stats stats) {
        String selected = null;
        for (SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.Tier tier : config.getTiers()) {
            if (matches(tier.getEnterWhen(), stats)) {
                selected = tier.getName();
            }
        }

        if (selected == null && !config.getTiers().isEmpty()) {
            selected = config.getTiers().get(0).getName();
        }
        return selected;
    }

    private boolean matches(SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.EnterWhen when, Stats stats) {
        if (when == null) {
            return false;
        }

        boolean specified = false;

        if (Boolean.TRUE.equals(when.getAlways())) {
            return true;
        }

        if (when.getSoftfailRateGte() != null) {
            specified = true;
            if (stats.softfailRate() < when.getSoftfailRateGte()) {
                return false;
            }
        }
        if (when.getHardfailRateGte() != null) {
            specified = true;
            if (stats.hardfailRate() < when.getHardfailRateGte()) {
                return false;
            }
        }
        if (when.getSoftfailConsecutiveGte() != null) {
            specified = true;
            if (stats.softfailConsecutive < when.getSoftfailConsecutiveGte()) {
                return false;
            }
        }
        if (when.getDisconnectCountGte() != null) {
            specified = true;
            if (stats.disconnect < when.getDisconnectCountGte()) {
                return false;
            }
        }

        return specified;
    }

    private String applyCooldown(State state, String desiredTierName, long now) {
        if (desiredTierName == null) {
            return state.currentTierName;
        }

        if (state.currentTierName == null) {
            state.currentTierName = desiredTierName;
            return state.currentTierName;
        }

        Map<String, Integer> severityByTier = severityMap();
        int currentSeverity = severityByTier.getOrDefault(normalize(state.currentTierName), 0);
        int desiredSeverity = severityByTier.getOrDefault(normalize(desiredTierName), currentSeverity);

        if (desiredSeverity > currentSeverity) {
            metricsRecorder.recordTierTransition(state.currentTierName, desiredTierName);
            state.currentTierName = desiredTierName;
            return state.currentTierName;
        }

        if (desiredSeverity < currentSeverity) {
            long cooldownMillis = toMillis(config.getCooldown());
            if (cooldownMillis <= 0 || state.lastFailureMillis == 0L || (now - state.lastFailureMillis >= cooldownMillis)) {
                metricsRecorder.recordTierTransition(state.currentTierName, desiredTierName);
                state.currentTierName = desiredTierName;
            }
        }

        return state.currentTierName;
    }

    private Map<String, Integer> severityMap() {
        Map<String, Integer> map = new HashMap<>();
        List<SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.Tier> tiers = config.getTiers();
        for (int i = 0; i < tiers.size(); i++) {
            map.put(normalize(tiers.get(i).getName()), i);
        }
        return map;
    }

    private SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.Tier findTier(String tierName) {
        if (tierName == null) {
            return null;
        }
        String normalized = normalize(tierName);
        for (SimulatorSmtpProperties.PolicySet.AdaptiveRateControl.Tier tier : config.getTiers()) {
            if (normalize(tier.getName()).equals(normalized)) {
                return tier;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static long toMillis(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return 0L;
        }
        return duration.toMillis();
    }

    private static final class State {
        private final ArrayDeque<Event> events = new ArrayDeque<>();
        private String currentTierName;
        private long lastFailureMillis;
    }

    private record Event(long timeMillis, EventType type) {
    }

    private enum EventType {
        ALLOW,
        SOFTFAIL,
        HARDFAIL,
        DISCONNECT
    }

    private static final class Stats {
        private int total;
        private int softfail;
        private int hardfail;
        private int disconnect;
        private int softfailConsecutive;

        private double softfailRate() {
            if (total == 0) {
                return 0;
            }
            return softfail / (double) total;
        }

        private double hardfailRate() {
            if (total == 0) {
                return 0;
            }
            return hardfail / (double) total;
        }
    }
}
