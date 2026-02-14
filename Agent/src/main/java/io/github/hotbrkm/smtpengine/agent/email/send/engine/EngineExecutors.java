package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Centrally manages all executors used by the engine.
 * <ul>
 *   <li><b>periodicExecutor</b> — Dedicated for periodic tasks (dispatchOnce, evict, etc.). corePoolSize automatically increases upon registration.</li>
 *   <li><b>oneshotExecutor</b> — Dedicated for delayed one-shot tasks (requeue, retry scheduling). corePoolSize=1.</li>
 *   <li><b>workerExecutor</b> — Fixed thread pool for EmailBatchSender execution.</li>
 *   <li><b>completionExecutor</b> — Virtual thread dedicated to waiting for worker completion (future.get()).</li>
 * </ul>
 */
@Slf4j
final class EngineExecutors {

    private final ScheduledThreadPoolExecutor periodicExecutor;
    private final ScheduledThreadPoolExecutor oneshotExecutor;
    private final ThreadPoolExecutor workerExecutor;
    private final ExecutorService completionExecutor;

    EngineExecutors(int workerCount) {
        this.periodicExecutor = new ScheduledThreadPoolExecutor(0);
        this.periodicExecutor.setRemoveOnCancelPolicy(true);

        this.oneshotExecutor = new ScheduledThreadPoolExecutor(1);
        this.oneshotExecutor.setRemoveOnCancelPolicy(true);

        this.workerExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(workerCount);
        this.completionExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Registers a periodic task. Automatically increases corePoolSize.
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        int required = periodicExecutor.getCorePoolSize() + 1;
        periodicExecutor.setCorePoolSize(required);
        log.debug("Periodic executor corePoolSize increased to {}", required);
        return periodicExecutor.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    /**
     * Schedules a delayed one-shot task (requeue, retry, etc.).
     */
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return oneshotExecutor.schedule(command, delay, unit);
    }

    /**
     * Submits a worker (EmailBatchSender).
     */
    <T> Future<T> submitWorker(Callable<T> task) {
        return workerExecutor.submit(task);
    }

    /**
     * Executes a completion waiting task on a virtual thread.
     */
    <T> CompletableFuture<T> supplyOnCompletion(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, completionExecutor);
    }

    /**
     * Returns the current number of active threads in the worker pool.
     */
    int activeWorkerCount() {
        return workerExecutor.getActiveCount();
    }

    /**
     * Returns the maximum number of threads in the worker pool.
     */
    int maxWorkerCount() {
        return workerExecutor.getMaximumPoolSize();
    }

    /**
     * Shuts down all executors.
     */
    void shutdown() {
        periodicExecutor.shutdown();
        oneshotExecutor.shutdown();
        workerExecutor.shutdown();
        completionExecutor.shutdown();

        try {
            if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
            if (!periodicExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                periodicExecutor.shutdownNow();
            }
            if (!oneshotExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                oneshotExecutor.shutdownNow();
            }
            if (!completionExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                completionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
            periodicExecutor.shutdownNow();
            oneshotExecutor.shutdownNow();
            completionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
