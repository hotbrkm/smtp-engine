package io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Queue managing batches per domain.
 * Maintains a separate queue for each domain to provide O(1) access performance.
 * <p>
 * Synchronization is managed independently by each {@link LaneState} with its own monitor lock.
 * Thus, operations on different lanes can be performed concurrently.
 * Cross-lane aggregation methods ({@link #getTotalBatchCount()}, {@link #getDomainStatistics()}, etc.)
 * query each lane sequentially, so point-in-time consistency is not guaranteed and should be used for monitoring/statistics purposes.
 */
@Slf4j
public class DomainBatchQueue {

    private static final List<DispatchLane> DEFAULT_POLL_ORDER = List.of(
            DispatchLane.REALTIME_FRESH, DispatchLane.REALTIME_RETRY, DispatchLane.BATCH_FRESH, DispatchLane.BATCH_RETRY
    );

    private final Map<DispatchLane, LaneState> laneStates;

    public DomainBatchQueue() {
        this.laneStates = new EnumMap<>(DispatchLane.class);
        for (DispatchLane lane : DispatchLane.values()) {
            laneStates.put(lane, new LaneState(lane));
        }
    }

    /**
     * Adds a batch to the queue.
     *
     * @param task Batch task to add
     */
    public void offer(DomainBatchTask task) {
        DispatchLane taskLane = task != null ? task.getDispatchLane() : null;
        offer(taskLane, task);
    }

    /**
     * Adds a batch to the queue in the specified lane.
     *
     * @param lane lane
     * @param task Batch task to add
     */
    public void offer(DispatchLane lane, DomainBatchTask task) {
        if (task == null) {
            return;
        }

        DispatchLane targetLane = lane != null ? lane : task.getDispatchLane();
        laneState(targetLane).offer(task);
    }

    /**
     * @deprecated Use {@link #offer(DispatchLane, DomainBatchTask)}.
     */
    @Deprecated(since = "0.1.0")
    public void offer(TaskLane lane, DomainBatchTask task) {
        offer(TaskLane.toDispatchLane(lane), task);
    }

    /**
     * Iterates through lanes in priority order and retrieves one available batch.
     *
     * @param excludedDomains List of excluded domains
     * @return Batch task, or null if none
     */
    public DomainBatchTask poll(Set<String> excludedDomains) {
        for (DispatchLane lane : DEFAULT_POLL_ORDER) {
            DomainBatchTask task = laneState(lane).poll(excludedDomains);
            if (task != null) {
                return task;
            }
        }
        return null;
    }

    /**
     * Retrieves one batch from the specified lane.
     *
     * @param lane            lane
     * @param excludedDomains List of excluded domains
     * @return Batch task, or null if none
     */
    public DomainBatchTask poll(DispatchLane lane, Set<String> excludedDomains) {
        return laneState(lane).poll(excludedDomains);
    }

    /**
     * @deprecated Use {@link #poll(DispatchLane, Set)}.
     */
    @Deprecated(since = "0.1.0")
    public DomainBatchTask poll(TaskLane lane, Set<String> excludedDomains) {
        return poll(TaskLane.toDispatchLane(lane), excludedDomains);
    }

    /**
     * Returns the number of batches for a specific domain.
     *
     * @param domain Domain
     * @return Batch count
     */
    public int getDomainBatchCount(String domain) {
        int total = 0;
        for (LaneState laneState : laneStates.values()) {
            total += laneState.getDomainBatchCount(domain);
        }
        return total;
    }

    /**
     * Returns the total number of batches.
     *
     * @return Total number of batches
     */
    public int getTotalBatchCount() {
        int total = 0;
        for (LaneState laneState : laneStates.values()) {
            total += laneState.getTotalBatchCount();
        }
        return total;
    }

    /**
     * Returns the total number of batches in a specific lane.
     */
    public int getTotalBatchCount(DispatchLane lane) {
        return laneState(lane).getTotalBatchCount();
    }

    /**
     * @deprecated Use {@link #getTotalBatchCount(DispatchLane)}.
     */
    @Deprecated(since = "0.1.0")
    public int getTotalBatchCount(TaskLane lane) {
        return getTotalBatchCount(TaskLane.toDispatchLane(lane));
    }

    /**
     * Returns the number of domains with batches.
     *
     * @return Domain count
     */
    public int getActiveDomainCount() {
        int total = 0;
        for (LaneState laneState : laneStates.values()) {
            total += laneState.getActiveDomainCount();
        }
        return total;
    }

    /**
     * Checks if the queue is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        for (LaneState laneState : laneStates.values()) {
            if (!laneState.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a specific lane is empty.
     */
    public boolean isEmpty(DispatchLane lane) {
        return laneState(lane).isEmpty();
    }

    /**
     * @deprecated Use {@link #isEmpty(DispatchLane)}.
     */
    @Deprecated(since = "0.1.0")
    public boolean isEmpty(TaskLane lane) {
        return isEmpty(TaskLane.toDispatchLane(lane));
    }

    /**
     * Checks if there is an available batch.
     *
     * @param excludedDomains List of excluded domains
     * @return true if there is an available batch
     */
    public boolean hasAvailableBatch(Set<String> excludedDomains) {
        for (DispatchLane lane : DEFAULT_POLL_ORDER) {
            if (laneState(lane).hasAvailableBatch(excludedDomains)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAvailableBatch(DispatchLane lane, Set<String> excludedDomains) {
        return laneState(lane).hasAvailableBatch(excludedDomains);
    }

    /**
     * @deprecated Use {@link #hasAvailableBatch(DispatchLane, Set)}.
     */
    @Deprecated(since = "0.1.0")
    public boolean hasAvailableBatch(TaskLane lane, Set<String> excludedDomains) {
        return hasAvailableBatch(TaskLane.toDispatchLane(lane), excludedDomains);
    }

    /**
     * Returns the age(ms) of the oldest waiting batch in a specific lane, excluding excluded domains.
     */
    public long getOldestAgeMillis(DispatchLane lane, Set<String> excludedDomains) {
        return laneState(lane).getOldestAgeMillis(excludedDomains);
    }

    /**
     * @deprecated Use {@link #getOldestAgeMillis(DispatchLane, Set)}.
     */
    @Deprecated(since = "0.1.0")
    public long getOldestAgeMillis(TaskLane lane, Set<String> excludedDomains) {
        return getOldestAgeMillis(TaskLane.toDispatchLane(lane), excludedDomains);
    }

    /**
     * Returns statistics for all domains.
     *
     * @return Map of batch counts per domain
     */
    public Map<String, Integer> getDomainStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        for (LaneState laneState : laneStates.values()) {
            laneState.collectDomainStatistics(stats);
        }
        return stats;
    }

    /**
     * Clears the queue.
     */
    public void clear() {
        for (LaneState laneState : laneStates.values()) {
            laneState.clear();
        }
        log.info("Domain batch queue cleared");
    }

    /**
     * Removes batches for a specific runnerId from the queue.
     *
     * @param runnerId Target runnerId to remove
     * @return List of removed batches
     */
    public List<DomainBatchTask> removeByRunnerId(String runnerId) {
        return removeByRunnerId(runnerId, null);
    }

    /**
     * Removes batches for a specific runnerId (and optional token) from the queue.
     *
     * @param runnerId    Target runnerId to remove
     * @param runnerToken Runner identification token (if null, based on runnerId only)
     * @return List of removed batches
     */
    public List<DomainBatchTask> removeByRunnerId(String runnerId, Object runnerToken) {
        List<DomainBatchTask> removed = new ArrayList<>();
        if (runnerId == null || runnerId.isBlank()) {
            return removed;
        }

        for (LaneState laneState : laneStates.values()) {
            laneState.removeByRunnerId(runnerId, runnerToken, removed);
        }
        return removed;
    }

    private LaneState laneState(DispatchLane lane) {
        DispatchLane targetLane = lane != null ? lane : DispatchLane.BATCH_FRESH;
        return laneStates.get(targetLane);
    }

    /**
     * Manages the per-domain queue state for a single lane.
     * Encapsulates internal queue logic such as domain round-robin, batch addition/removal.
     * <p>
     * All public methods are synchronized with their own monitor lock ({@code synchronized}).
     */
    private static final class LaneState {
        private final DispatchLane lane;
        private final Map<String, Queue<DomainBatchTask>> domainQueues = new LinkedHashMap<>();
        private final Queue<String> availableDomains = new ArrayDeque<>();
        private int totalBatches;

        LaneState(DispatchLane lane) {
            this.lane = lane;
        }

        synchronized void offer(DomainBatchTask task) {
            String domain = task.getDomain();
            Queue<DomainBatchTask> queue = domainQueues.computeIfAbsent(domain, k -> new ArrayDeque<>());

            boolean wasEmpty = queue.isEmpty();
            queue.offer(task);
            totalBatches++;

            if (wasEmpty) {
                availableDomains.offer(domain);
            }

            log.debug("Batch added. lane={}, domain='{}', laneQueueSize={}, laneTotal={}",
                    lane, domain, queue.size(), totalBatches);
        }

        synchronized DomainBatchTask poll(Set<String> excludedDomains) {
            if (availableDomains.isEmpty()) {
                return null;
            }

            int attempts = availableDomains.size();
            for (int i = 0; i < attempts; i++) {
                String domain = availableDomains.poll();
                if (domain == null) {
                    break;
                }

                if (excludedDomains != null && excludedDomains.contains(domain)) {
                    availableDomains.offer(domain);
                    continue;
                }

                Queue<DomainBatchTask> queue = domainQueues.get(domain);
                if (queue == null || queue.isEmpty()) {
                    continue;
                }

                DomainBatchTask task = queue.poll();
                totalBatches--;

                if (!queue.isEmpty()) {
                    availableDomains.offer(domain);
                }

                log.debug("Batch polled. lane={}, domain='{}', laneRemaining={}, laneTotal={}",
                        lane, domain, queue.size(), totalBatches);

                return task;
            }
            return null;
        }

        synchronized boolean hasAvailableBatch(Set<String> excludedDomains) {
            if (availableDomains.isEmpty()) {
                return false;
            }

            if (excludedDomains == null || excludedDomains.isEmpty()) {
                return true;
            }

            for (String domain : availableDomains) {
                if (!excludedDomains.contains(domain)) {
                    Queue<DomainBatchTask> queue = domainQueues.get(domain);
                    if (queue != null && !queue.isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }

        synchronized long getOldestAgeMillis(Set<String> excludedDomains) {
            if (domainQueues.isEmpty()) {
                return 0L;
            }

            long oldestAge = 0L;
            for (Map.Entry<String, Queue<DomainBatchTask>> entry : domainQueues.entrySet()) {
                if (excludedDomains != null && excludedDomains.contains(entry.getKey())) {
                    continue;
                }

                Queue<DomainBatchTask> queue = entry.getValue();
                if (queue.isEmpty()) {
                    continue;
                }

                DomainBatchTask head = queue.peek();
                if (head != null) {
                    oldestAge = Math.max(oldestAge, head.getAge());
                }
            }
            return oldestAge;
        }

        synchronized int getDomainBatchCount(String domain) {
            Queue<DomainBatchTask> queue = domainQueues.get(domain);
            return queue != null ? queue.size() : 0;
        }

        synchronized int getTotalBatchCount() {
            return totalBatches;
        }

        synchronized int getActiveDomainCount() {
            return availableDomains.size();
        }

        synchronized boolean isEmpty() {
            return totalBatches == 0;
        }

        synchronized void collectDomainStatistics(Map<String, Integer> stats) {
            for (Map.Entry<String, Queue<DomainBatchTask>> entry : domainQueues.entrySet()) {
                stats.merge(entry.getKey(), entry.getValue().size(), Integer::sum);
            }
        }

        synchronized void removeByRunnerId(String runnerId, Object runnerToken, List<DomainBatchTask> removed) {
            for (Map.Entry<String, Queue<DomainBatchTask>> entry : domainQueues.entrySet()) {
                Queue<DomainBatchTask> queue = entry.getValue();
                if (queue.isEmpty()) {
                    continue;
                }

                List<DomainBatchTask> toRemove = new ArrayList<>();
                for (DomainBatchTask task : queue) {
                    if (!runnerId.equals(task.getRunnerId())) {
                        continue;
                    }
                    if (runnerToken != null && task.getResultWriter() != runnerToken) {
                        continue;
                    }
                    toRemove.add(task);
                }

                for (DomainBatchTask task : toRemove) {
                    if (queue.remove(task)) {
                        removed.add(task);
                    }
                }
            }
            rebuild();
        }

        synchronized void clear() {
            domainQueues.clear();
            availableDomains.clear();
            totalBatches = 0;
        }

        private void rebuild() {
            availableDomains.clear();
            int count = 0;
            for (Map.Entry<String, Queue<DomainBatchTask>> entry : domainQueues.entrySet()) {
                Queue<DomainBatchTask> queue = entry.getValue();
                if (!queue.isEmpty()) {
                    availableDomains.offer(entry.getKey());
                    count += queue.size();
                }
            }
            totalBatches = count;
        }
    }
}
