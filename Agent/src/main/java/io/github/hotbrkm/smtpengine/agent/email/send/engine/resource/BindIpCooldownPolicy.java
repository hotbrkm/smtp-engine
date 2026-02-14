package io.github.hotbrkm.smtpengine.agent.email.send.engine.resource;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Defines bind IP cooldown rules (trigger codes, threshold resolver, cooldown time).
 */
public record BindIpCooldownPolicy(Set<Integer> triggerCodes,
                            IntUnaryOperator thresholdResolver,
                            long cooldownMs) {

    public BindIpCooldownPolicy {
        Objects.requireNonNull(triggerCodes, "triggerCodes must not be null");
        Objects.requireNonNull(thresholdResolver, "thresholdResolver must not be null");

        Set<Integer> normalizedCodes = new HashSet<>();
        for (Integer code : triggerCodes) {
            if (code != null && code > 0) {
                normalizedCodes.add(code);
            }
        }

        triggerCodes = Collections.unmodifiableSet(normalizedCodes);
        cooldownMs = Math.max(0L, cooldownMs);
    }

    /**
     * Returns a disabled cooldown policy.
     */
    static BindIpCooldownPolicy disabled() {
        return new BindIpCooldownPolicy(Set.of(), code -> 1, 0L);
    }
}
