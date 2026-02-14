package io.github.hotbrkm.smtpengine.agent.email.send.engine.resource;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Class that allocates/reclaims bind IP session slots per domain.
 * <p>
 * - Session counts are managed by (domain, bindIp).
 * - Selection strategy is Least Usage First + Round Robin for ties.
 * - Cooldowns are managed by (domain, bindIp).
 */
@Slf4j
public class BindIpSessionAllocator {

    @Getter
    private final List<String> bindIps;
    private final EmailDomainManager emailDomainManager;
    private final BindIpCooldownPolicy cooldownPolicy;
    private final Map<String, DomainState> domainStates = new ConcurrentHashMap<>();

    public BindIpSessionAllocator(List<String> bindIps, EmailDomainManager emailDomainManager) {
        this(bindIps, emailDomainManager, BindIpCooldownPolicy.disabled());
    }

    public BindIpSessionAllocator(List<String> bindIps, EmailDomainManager emailDomainManager, BindIpCooldownPolicy cooldownPolicy) {
        this.bindIps = normalizeBindIps(bindIps);
        this.emailDomainManager = Objects.requireNonNull(emailDomainManager, "emailDomainManager must not be null");
        this.cooldownPolicy = Objects.requireNonNull(cooldownPolicy, "cooldownPolicy must not be null");
        if (this.bindIps.isEmpty()) {
            throw new IllegalArgumentException("bindIps must not be empty");
        }
    }

    /**
     * Allocates a bind IP slot considering per-IP limits provided by domain policy.
     * <p>
     * Selection policy:
     * 1) Exclude cooldown/saturated IPs
     * 2) Least Usage First
     * 3) Round Robin for ties
     *
     * @param domain Sending domain
     * @return Lease upon successful allocation
     */
    public Optional<BindIpLease> tryAcquire(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }

        int perIpLimit = emailDomainManager.getSessionLimit(domain);
        DomainState state = domainStates.computeIfAbsent(domain, k -> new DomainState());

        // Allow retries on CAS contention.
        for (int attempt = 0; attempt < 5; attempt++) {
            int min = Integer.MAX_VALUE;
            int start = Math.floorMod(state.rrCursor.getAndIncrement(), bindIps.size());

            for (String bindIp : bindIps) {
                if (isCoolingDown(state, bindIp)) {
                    continue;
                }
                AtomicInteger counter = state.activeByIp.computeIfAbsent(bindIp, k -> new AtomicInteger(0));
                int current = counter.get();
                if (current >= perIpLimit) {
                    continue;
                }
                if (current < min) {
                    min = current;
                }
            }

            if (min == Integer.MAX_VALUE) {
                return Optional.empty();
            }

            for (int offset = 0; offset < bindIps.size(); offset++) {
                String bindIp = bindIps.get((start + offset) % bindIps.size());
                if (isCoolingDown(state, bindIp)) {
                    continue;
                }

                AtomicInteger counter = state.activeByIp.computeIfAbsent(bindIp, k -> new AtomicInteger(0));
                int current = counter.get();
                if (current != min) {
                    continue;
                }
                if (counter.compareAndSet(current, current + 1)) {
                    return Optional.of(new BindIpLease(domain, bindIp, System.currentTimeMillis()));
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Returns a previously acquired lease and decrements the active count.
     *
     * @param lease Lease to return
     */
    public void release(BindIpLease lease) {
        if (lease == null) {
            return;
        }

        DomainState state = domainStates.get(lease.domain());
        if (state == null) {
            log.warn("Failed to release bind IP lease. Domain state missing: {}", lease.domain());
            return;
        }

        AtomicInteger counter = state.activeByIp.get(lease.bindIp());
        if (counter == null) {
            log.warn("Failed to release bind IP lease. Counter missing: {} / {}", lease.domain(), lease.bindIp());
            return;
        }

        int next = counter.decrementAndGet();
        if (next < 0) {
            counter.set(0);
            log.error("Bind IP session counter became negative. domain={}, bindIp={}", lease.domain(), lease.bindIp());
        }
    }

    /**
     * Updates cooldown status reflecting the batch attempt result.
     *
     * @return true if cooldown is triggered
     */
    public boolean recordBatchResult(String domain, String bindIp, int statusCode, boolean hasAnySuccess) {
        if (domain == null || bindIp == null) {
            return false;
        }

        DomainState state = domainStates.computeIfAbsent(domain, k -> new DomainState());
        if (hasAnySuccess) {
            resetCodeStreak(state, bindIp);
            return false;
        }

        if (!cooldownPolicy.triggerCodes().contains(statusCode)) {
            resetCodeStreak(state, bindIp);
            return false;
        }

        int threshold = Math.max(1, cooldownPolicy.thresholdResolver().applyAsInt(statusCode));
        int streak = incrementCodeStreak(state, bindIp, statusCode);
        if (streak < threshold) {
            return false;
        }

        long ttl = cooldownPolicy.cooldownMs();
        long until = System.currentTimeMillis() + ttl;
        state.cooldownUntilByIp.computeIfAbsent(bindIp, k -> new AtomicLong(0L)).set(until);
        resetCodeStreak(state, bindIp);
        log.info("Bind IP cooldown applied. domain={}, bindIp={}, statusCode={}, cooldownMs={}", domain, bindIp, statusCode, ttl);
        return true;
    }

    /**
     * Returns the total sum of active sessions for all domains/Bind IPs.
     */
    public int getTotalActiveSessionCount() {
        return domainStates.values().stream()
                .flatMap(state -> state.activeByIp.values().stream())
                .mapToInt(AtomicInteger::get)
                .sum();
    }

    private int incrementCodeStreak(DomainState state, String bindIp, int statusCode) {
        Map<Integer, AtomicInteger> codeMap = state.codeStreakByIp.computeIfAbsent(bindIp, k -> new ConcurrentHashMap<>());
        for (Map.Entry<Integer, AtomicInteger> entry : codeMap.entrySet()) {
            if (entry.getKey() != statusCode) {
                entry.getValue().set(0);
            }
        }
        AtomicInteger streak = codeMap.computeIfAbsent(statusCode, k -> new AtomicInteger(0));
        return streak.incrementAndGet();
    }

    private void resetCodeStreak(DomainState state, String bindIp) {
        Map<Integer, AtomicInteger> codeMap = state.codeStreakByIp.get(bindIp);
        if (codeMap == null) {
            return;
        }
        for (AtomicInteger value : codeMap.values()) {
            value.set(0);
        }
    }

    private boolean isCoolingDown(DomainState state, String bindIp) {
        AtomicLong until = state.cooldownUntilByIp.get(bindIp);
        return until != null && until.get() > System.currentTimeMillis();
    }

    private List<String> normalizeBindIps(List<String> rawBindIps) {
        if (rawBindIps == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String bindIp : rawBindIps) {
            if (bindIp == null) {
                continue;
            }
            String value = bindIp.trim();
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private static final class DomainState {
        private final AtomicInteger rrCursor = new AtomicInteger(0);
        private final Map<String, AtomicInteger> activeByIp = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> cooldownUntilByIp = new ConcurrentHashMap<>();
        private final Map<String, Map<Integer, AtomicInteger>> codeStreakByIp = new ConcurrentHashMap<>();
    }
}
