package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyReason;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class FaultInjectionRule implements PolicyRule {

    private static final int SUCCESS_CODE = 250;

    private final SimulatorSmtpProperties.PolicySet.FaultInjection config;
    private final FailureSelector selector;

    public FaultInjectionRule(SimulatorSmtpProperties.PolicySet.FaultInjection config) {
        this.config = config;
        this.selector = createSelector(config);
    }

    @Override
    public PolicyOutcome evaluate(PolicyContext context) {
        int code = selector.nextCode();
        if (code == SUCCESS_CODE) {
            return PolicyOutcome.allow();
        }

        PolicyOutcome outcome;
        if (config.getActions() != null && config.getActions().isAllowDisconnect() && code == 421) {
            outcome = PolicyOutcome.disconnect(code,
                    "4.7.0 Session closed by fault injection.",
                    PolicyReason.FAULT_INJECTION);
        } else if (code >= 500) {
            outcome = PolicyOutcome.permFail(code,
                    "5.0.0 Injected failure.",
                    PolicyReason.FAULT_INJECTION);
        } else {
            outcome = PolicyOutcome.tempFail(code,
                    "4.0.0 Injected temporary failure.",
                    PolicyReason.FAULT_INJECTION);
        }

        long delay = 0L;
        if (config.getActions() != null && config.getActions().isAllowDelay()) {
            delay = toMillis(config.getActions().getMaxDelay());
            if (delay > 0) {
                delay = ThreadLocalRandom.current().nextLong(delay + 1);
            }
        }

        PolicyOutcome finalOutcome = outcome.withSynthetic(true);
        if (delay > 0) {
            finalOutcome = finalOutcome.withDelay(delay);
        }
        return finalOutcome;
    }

    private static FailureSelector createSelector(SimulatorSmtpProperties.PolicySet.FaultInjection config) {
        if (config == null || config.getDistribution() == null || config.getDistribution().isEmpty()) {
            return () -> SUCCESS_CODE;
        }

        Map<Integer, Integer> distribution = normalizeDistribution(config.getDistribution());
        if (distribution.isEmpty()) {
            return () -> SUCCESS_CODE;
        }

        validateDistribution(distribution);

        String mode = config.getMode() == null ? "random" : config.getMode().trim().toLowerCase();
        if ("windowed".equals(mode)) {
            int windowSize = config.getWindowSize() == null || config.getWindowSize() <= 0
                    ? 100
                    : config.getWindowSize();
            return new WindowedSelector(distribution, windowSize, newIntRandom(config.getSeed()));
        }
        return new RandomSelector(distribution, newIntRandom(config.getSeed()));
    }

    private static IntRandom newIntRandom(Long seed) {
        if (seed == null) {
            return bound -> ThreadLocalRandom.current().nextInt(bound);
        }
        Random random = new Random(seed);
        return bound -> {
            synchronized (random) {
                return random.nextInt(bound);
            }
        };
    }

    private static Map<Integer, Integer> normalizeDistribution(Map<Integer, Integer> input) {
        Map<Integer, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (entry.getValue() <= 0) {
                continue;
            }
            normalized.put(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    private static void validateDistribution(Map<Integer, Integer> distribution) {
        int sum = 0;
        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            int code = entry.getKey();
            int percent = entry.getValue();
            if (code < 400 || code > 599) {
                throw new IllegalArgumentException("fault-injection.distribution code must be 400-599: " + code);
            }
            sum += percent;
        }
        if (sum > 100) {
            throw new IllegalArgumentException("fault-injection.distribution sum must be <= 100: " + sum);
        }
    }

    private static long toMillis(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return 0L;
        }
        return duration.toMillis();
    }

    private interface FailureSelector {
        int nextCode();
    }

    private interface IntRandom {
        int nextInt(int bound);
    }

    private static final class RandomSelector implements FailureSelector {

        private final List<Range> ranges;
        private final int failureThreshold;
        private final IntRandom random;

        private RandomSelector(Map<Integer, Integer> distribution, IntRandom random) {
            this.random = random;
            this.ranges = new ArrayList<>();
            int cursor = 0;
            for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
                cursor += entry.getValue();
                ranges.add(new Range(cursor, entry.getKey()));
            }
            this.failureThreshold = cursor;
        }

        @Override
        public int nextCode() {
            int draw = random.nextInt(100) + 1;
            if (draw > failureThreshold) {
                return SUCCESS_CODE;
            }
            for (Range range : ranges) {
                if (draw <= range.upperBound) {
                    return range.code;
                }
            }
            return SUCCESS_CODE;
        }

        private record Range(int upperBound, int code) {
        }
    }

    private static final class WindowedSelector implements FailureSelector {

        private final Map<Integer, Integer> distribution;
        private final int windowSize;
        private final IntRandom random;
        private final Deque<Integer> queue = new ArrayDeque<>();

        private WindowedSelector(Map<Integer, Integer> distribution, int windowSize, IntRandom random) {
            this.distribution = distribution;
            this.windowSize = Math.max(1, windowSize);
            this.random = random;
        }

        @Override
        public int nextCode() {
            synchronized (queue) {
                if (queue.isEmpty()) {
                    refill();
                }
                Integer code = queue.pollFirst();
                return code == null ? SUCCESS_CODE : code;
            }
        }

        private void refill() {
            List<Quota> quotas = new ArrayList<>();
            int allocated = 0;
            for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
                double exact = entry.getValue() * windowSize / 100.0;
                int count = (int) Math.floor(exact);
                quotas.add(new Quota(entry.getKey(), count, exact - count));
                allocated += count;
            }

            int successPercent = 100 - distribution.values().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            successPercent = Math.max(0, successPercent);
            double successExact = successPercent * windowSize / 100.0;
            Quota successQuota = new Quota(SUCCESS_CODE, (int) Math.floor(successExact), successExact - Math.floor(successExact));
            allocated += successQuota.count;

            int missing = windowSize - allocated;
            List<Quota> all = new ArrayList<>(quotas);
            all.add(successQuota);

            if (missing > 0) {
                all.sort((a, b) -> Double.compare(b.remainder, a.remainder));
                for (int i = 0; i < missing; i++) {
                    all.get(i % all.size()).count++;
                }
            } else if (missing < 0) {
                all.sort(Comparator.comparingDouble(a -> a.remainder));
                int remove = -missing;
                for (Quota quota : all) {
                    while (remove > 0 && quota.count > 0) {
                        quota.count--;
                        remove--;
                    }
                    if (remove == 0) {
                        break;
                    }
                }
            }

            List<Integer> values = new ArrayList<>(windowSize);
            for (Quota quota : all) {
                for (int i = 0; i < quota.count; i++) {
                    values.add(quota.code);
                }
            }
            shuffle(values);
            queue.addAll(values);
        }

        private void shuffle(List<Integer> values) {
            for (int i = values.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                Collections.swap(values, i, j);
            }
        }

        private static final class Quota {
            private final int code;
            private int count;
            private final double remainder;

            private Quota(int code, int count, double remainder) {
                this.code = code;
                this.count = count;
                this.remainder = remainder;
            }
        }
    }
}
