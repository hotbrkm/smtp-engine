package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResultWriter;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailSendProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DispatchLane;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DomainBatchQueue")
class DomainBatchQueueTest {

    @Nested
    @DisplayName("Round-robin and exclusion domains")
    class RoundRobinAndExclusion {

        @Test
        @DisplayName("Should verify round-robin poll order between two domains")
        void testRoundRobinBetweenDomains() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", 1));
            queue.offer(task("b.com", 1));
            queue.offer(task("a.com", 1));
            queue.offer(task("b.com", 1));

            List<String> polled = new ArrayList<>();
            polled.add(queue.poll(Collections.emptySet()).getDomain());
            polled.add(queue.poll(Collections.emptySet()).getDomain());
            polled.add(queue.poll(Collections.emptySet()).getDomain());
            polled.add(queue.poll(Collections.emptySet()).getDomain());

            assertThat(polled).containsExactly("a.com", "b.com", "a.com", "b.com");
            assertThat(queue.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("Should prioritize polling other domains by ignoring excluded domains")
        void testExcludedDomains() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", 1));
            queue.offer(task("b.com", 1));

            DomainBatchTask t1 = queue.poll(Set.of("a.com"));
            DomainBatchTask t2 = queue.poll(Set.of("a.com"));

            assertThat(t1.getDomain()).isEqualTo("b.com");
            assertThat(t2).isNull();
        }

        @Test
        @DisplayName("Should be able to poll remaining batch after exclusion is released")
        void testExcludedDomainReleasedAfterwards() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", 1));
            queue.offer(task("b.com", 1));

            assertThat(queue.poll(Set.of("a.com")).getDomain()).isEqualTo("b.com");
            assertThat(queue.poll(Set.of("a.com"))).isNull();

            // After exclusion is released, a.com batch can be polled
            DomainBatchTask remaining = queue.poll(Collections.emptySet());
            assertThat(remaining).isNotNull();
            assertThat(remaining.getDomain()).isEqualTo("a.com");
            assertThat(queue.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("Lane priority")
    class LanePriority {

        @Test
        @DisplayName("Default poll should prioritize REALTIME lane over BATCH lane")
        void testRealtimeBeforeBatch() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("batch.com", ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));
            queue.offer(task("rt.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));

            assertThat(queue.poll(Collections.emptySet()).getDomain()).isEqualTo("rt.com");
            assertThat(queue.poll(Collections.emptySet()).getDomain()).isEqualTo("batch.com");
        }

        @Test
        @DisplayName("4-lane full priority order: REALTIME_FRESH > REALTIME_RETRY > BATCH_FRESH > BATCH_RETRY")
        void testFullLanePollOrder() {
            DomainBatchQueue queue = new DomainBatchQueue();
            // Offer in reverse order to verify priority is by lane order, not insertion order
            queue.offer(task("d.com", ExecutionMode.BATCH, DispatchLane.BATCH_RETRY));
            queue.offer(task("c.com", ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));
            queue.offer(task("b.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_RETRY));
            queue.offer(task("a.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));

            List<String> polled = new ArrayList<>();
            DomainBatchTask task;
            while ((task = queue.poll(Collections.emptySet())) != null) {
                polled.add(task.getDomain());
            }

            assertThat(polled).containsExactly("a.com", "b.com", "c.com", "d.com");
        }

        @Test
        @DisplayName("Round-robin is maintained within the same priority lane")
        void testRoundRobinWithinSameLane() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("x.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));
            queue.offer(task("y.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));
            queue.offer(task("x.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));

            assertThat(queue.poll(Collections.emptySet()).getDomain()).isEqualTo("x.com");
            assertThat(queue.poll(Collections.emptySet()).getDomain()).isEqualTo("y.com");
            assertThat(queue.poll(Collections.emptySet()).getDomain()).isEqualTo("x.com");
        }
    }

    @Nested
    @DisplayName("Lane-specific offer/poll")
    class LaneSpecificOperations {

        @Test
        @DisplayName("Should offer to specific lane and poll only from that lane")
        void testOfferAndPollByLane() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(DispatchLane.REALTIME_FRESH, task("a.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));
            queue.offer(DispatchLane.BATCH_FRESH, task("b.com", ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));

            // Polling from BATCH_FRESH returns only b.com
            DomainBatchTask fromBatch = queue.poll(DispatchLane.BATCH_FRESH, Collections.emptySet());
            assertThat(fromBatch.getDomain()).isEqualTo("b.com");
            assertThat(queue.poll(DispatchLane.BATCH_FRESH, Collections.emptySet())).isNull();

            // REALTIME_FRESH still has a.com remaining
            DomainBatchTask fromRt = queue.poll(DispatchLane.REALTIME_FRESH, Collections.emptySet());
            assertThat(fromRt.getDomain()).isEqualTo("a.com");
        }

        @Test
        @DisplayName("Should query per-lane batch count individually")
        void testPerLaneCountAndEmpty() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));
            queue.offer(task("b.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));
            queue.offer(task("c.com", ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));

            assertThat(queue.getTotalBatchCount(DispatchLane.REALTIME_FRESH)).isEqualTo(2);
            assertThat(queue.getTotalBatchCount(DispatchLane.BATCH_FRESH)).isEqualTo(1);
            assertThat(queue.getTotalBatchCount(DispatchLane.REALTIME_RETRY)).isZero();
            assertThat(queue.isEmpty(DispatchLane.REALTIME_RETRY)).isTrue();
            assertThat(queue.isEmpty(DispatchLane.REALTIME_FRESH)).isFalse();
            assertThat(queue.getTotalBatchCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("hasAvailableBatch")
    class HasAvailableBatch {

        @Test
        @DisplayName("Should return false when all domains are excluded")
        void testAllExcluded() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", 1));
            queue.offer(task("b.com", 1));

            assertThat(queue.hasAvailableBatch(Set.of("a.com", "b.com"))).isFalse();
        }

        @Test
        @DisplayName("Should return true when there are non-excluded domains")
        void testPartiallyExcluded() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", 1));
            queue.offer(task("b.com", 1));

            assertThat(queue.hasAvailableBatch(Set.of("a.com"))).isTrue();
        }

        @Test
        @DisplayName("Should query hasAvailableBatch for specific lane")
        void testHasAvailableBatchByLane() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));

            assertThat(queue.hasAvailableBatch(DispatchLane.REALTIME_FRESH, Collections.emptySet())).isTrue();
            assertThat(queue.hasAvailableBatch(DispatchLane.BATCH_FRESH, Collections.emptySet())).isFalse();
        }
    }

    @Nested
    @DisplayName("removeByRunnerId")
    class RemoveByRunnerId {

        @Test
        @DisplayName("Should remove waiting batches by runnerId/token")
        void testRemoveByRunnerIdWithToken() {
            DomainBatchQueue queue = new DomainBatchQueue();
            EmailBatchResultWriter runnerTokenA = createMockWriter();
            EmailBatchResultWriter runnerTokenB = createMockWriter();
            try {
                queue.offer(task("a.com", "runner-1", runnerTokenA));
                queue.offer(task("b.com", "runner-1", runnerTokenB));
                queue.offer(task("c.com", "runner-2", runnerTokenA));

                List<DomainBatchTask> removed = queue.removeByRunnerId("runner-1", runnerTokenA);

                assertThat(removed).hasSize(1);
                assertThat(removed.getFirst().getDomain()).isEqualTo("a.com");
                assertThat(queue.getTotalBatchCount()).isEqualTo(2);
            } finally {
                runnerTokenA.close();
                runnerTokenB.close();
            }
        }

        @Test
        @DisplayName("Should remove batches across multiple lanes by runnerId")
        void testRemoveAcrossLanes() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", "runner-1", null, ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));
            queue.offer(task("b.com", "runner-1", null, ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));
            queue.offer(task("c.com", "runner-2", null, ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));

            List<DomainBatchTask> removed = queue.removeByRunnerId("runner-1");

            assertThat(removed).hasSize(2);
            assertThat(removed).extracting(DomainBatchTask::getDomain).containsExactlyInAnyOrder("a.com", "b.com");
            assertThat(queue.getTotalBatchCount()).isEqualTo(1);
            assertThat(queue.getTotalBatchCount(DispatchLane.REALTIME_FRESH)).isEqualTo(1);
            assertThat(queue.getTotalBatchCount(DispatchLane.BATCH_FRESH)).isZero();
        }
    }

    @Nested
    @DisplayName("Statistics query")
    class Statistics {

        @Test
        @DisplayName("getDomainStatistics should aggregate same domain count across lanes")
        void testDomainStatisticsAcrossLanes() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));
            queue.offer(task("a.com", ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));
            queue.offer(task("b.com", ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));

            Map<String, Integer> stats = queue.getDomainStatistics();

            assertThat(stats).containsEntry("a.com", 2);
            assertThat(stats).containsEntry("b.com", 1);
        }

        @Test
        @DisplayName("getDomainBatchCount should return aggregated result across lanes")
        void testDomainBatchCountAcrossLanes() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));
            queue.offer(task("a.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_RETRY));
            queue.offer(task("a.com", ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));

            assertThat(queue.getDomainBatchCount("a.com")).isEqualTo(3);
            assertThat(queue.getDomainBatchCount("unknown.com")).isZero();
        }

        @Test
        @DisplayName("getActiveDomainCount should aggregate active domain count across all lanes")
        void testActiveDomainCountAcrossLanes() {
            DomainBatchQueue queue = new DomainBatchQueue();
            queue.offer(task("a.com", ExecutionMode.REALTIME, DispatchLane.REALTIME_FRESH));
            queue.offer(task("a.com", ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));
            queue.offer(task("b.com", ExecutionMode.BATCH, DispatchLane.BATCH_FRESH));

            // REALTIME_FRESH: a.com(1), BATCH_FRESH: a.com, b.com(2) = total 3
            assertThat(queue.getActiveDomainCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Cross-lane concurrency")
    class CrossLaneConcurrency {

        @Test
        @DisplayName("Should have no data loss during concurrent offer/poll across different lanes")
        void testConcurrentOfferAndPollAcrossLanes() throws Exception {
            DomainBatchQueue queue = new DomainBatchQueue();
            int taskCountPerLane = 1_000;
            DispatchLane[] lanes = DispatchLane.values();
            int threadCount = lanes.length * 2; // 1 offer + 1 poll per lane

            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            CountDownLatch offersDone = new CountDownLatch(lanes.length);
            AtomicInteger totalPolled = new AtomicInteger();
            List<Throwable> errors = new CopyOnWriteArrayList<>();

            // Create offer thread + poll thread for each lane
            List<Thread> threads = new ArrayList<>();
            for (DispatchLane lane : lanes) {
                ExecutionMode mode = lane.isRealtime() ? ExecutionMode.REALTIME : ExecutionMode.BATCH;

                // offer thread
                Thread offerThread = Thread.ofVirtual().unstarted(() -> {
                    try {
                        barrier.await();
                        for (int i = 0; i < taskCountPerLane; i++) {
                            queue.offer(lane, task("domain-" + i + ".com", mode, lane));
                        }
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        offersDone.countDown();
                    }
                });

                // poll thread
                Thread pollThread = Thread.ofVirtual().unstarted(() -> {
                    try {
                        barrier.await();
                        offersDone.await(); // Start polling after all offers are done
                        while (queue.poll(lane, Collections.emptySet()) != null) {
                            totalPolled.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.add(e);
                    }
                });

                threads.add(offerThread);
                threads.add(pollThread);
            }

            threads.forEach(Thread::start);
            for (Thread t : threads) {
                t.join(10_000);
            }

            assertThat(errors).isEmpty();
            assertThat(totalPolled.get()).isEqualTo(taskCountPerLane * lanes.length);
            assertThat(queue.isEmpty()).isTrue();
            assertThat(queue.getTotalBatchCount()).isZero();
        }
    }

    // -- test helpers --

    private DomainBatchTask task(String domain, int size) {
        List<EmailSendTarget> batch = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            batch.add(EmailSendTarget.builder().targetEmail("t@" + domain).targetData(Collections.emptyMap()).build());
        }
        return new DomainBatchTask(batch, domain, domain + "-id", null, 0, "test-runner", null, null,
                ExecutionMode.BATCH, DispatchLane.BATCH_FRESH);
    }

    private DomainBatchTask task(String domain, String runnerId, EmailBatchResultWriter runnerToken) {
        List<EmailSendTarget> batch = List.of(EmailSendTarget.builder().targetEmail("t@" + domain).targetData(Collections.emptyMap()).build());
        return new DomainBatchTask(batch, domain, domain + "-id", null, 0, runnerId, runnerToken, null,
                ExecutionMode.BATCH, DispatchLane.BATCH_FRESH);
    }

    private DomainBatchTask task(String domain, String runnerId, EmailBatchResultWriter runnerToken,
                                 ExecutionMode mode, DispatchLane lane) {
        List<EmailSendTarget> batch = List.of(EmailSendTarget.builder().targetEmail("t@" + domain).targetData(Collections.emptyMap()).build());
        return new DomainBatchTask(batch, domain, domain + "-id", null, 0, runnerId, runnerToken, null, mode, lane);
    }

    private DomainBatchTask task(String domain, ExecutionMode mode, DispatchLane lane) {
        List<EmailSendTarget> batch = List.of(EmailSendTarget.builder().targetEmail("t@" + domain).targetData(Collections.emptyMap()).build());
        return new DomainBatchTask(batch, domain, domain + "-id", null, 0, "test-runner", null, null, mode, lane);
    }

    private EmailBatchResultWriter createMockWriter() {
        return new EmailBatchResultWriter() {
            @Override public void writeResult(EmailSendProgress progress) {}
            @Override public void close() {}
        };
    }
}
