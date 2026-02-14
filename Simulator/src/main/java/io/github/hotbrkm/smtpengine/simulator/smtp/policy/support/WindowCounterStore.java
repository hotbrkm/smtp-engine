package io.github.hotbrkm.smtpengine.simulator.smtp.policy.support;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WindowCounterStore {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, long windowMillis, int maxCount) {
        if (windowMillis <= 0 || maxCount <= 0) {
            return true;
        }
        String normalizedKey = key == null ? "global" : key;
        WindowCounter counter = counters.computeIfAbsent(normalizedKey, k -> new WindowCounter());
        return counter.tryAcquire(windowMillis, maxCount);
    }

    private static final class WindowCounter {
        private final AtomicInteger count = new AtomicInteger();
        private volatile long windowStartMillis = System.currentTimeMillis();

        boolean tryAcquire(long windowMillis, int maxCount) {
            long now = System.currentTimeMillis();
            if (now - windowStartMillis >= windowMillis) {
                synchronized (this) {
                    if (now - windowStartMillis >= windowMillis) {
                        windowStartMillis = now;
                        count.set(0);
                    }
                }
            }

            int current = count.incrementAndGet();
            if (current > maxCount) {
                count.decrementAndGet();
                return false;
            }
            return true;
        }
    }
}
