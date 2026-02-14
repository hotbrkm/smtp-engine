package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import java.util.Map;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchTask;

/**
 * Utility responsible for cleaning up resource wait time tracking maps.
 */
final class WaitTrackingSupport {

    private WaitTrackingSupport() {
    }

    /**
     * Generates a key for wait tracking.
     */
    static String waitTrackingKey(DomainBatchTask task) {
        if (task == null) {
            return "unknown-task";
        }
        if (task.getBatchId() != null && !task.getBatchId().isBlank()) {
            return task.getBatchId();
        }
        return "unknown-task-" + System.identityHashCode(task);
    }

    /**
     * Removes all wait tracking states associated with a specific task.
     */
    static void removeWaitTrackingForTask(EngineRuntimeState runtimeState, DomainBatchTask task) {
        if (runtimeState == null || task == null) {
            return;
        }
        String waitKey = waitTrackingKey(task);
        runtimeState.bindIpWaitStartByBatchId().remove(waitKey);
        for (ResourceSlotGroup group : runtimeState.resourceSlotGroups().values()) {
            group.attachmentWaitStartByBatchId().remove(waitKey);
        }
        removeWaitTrackingForBatch(runtimeState, task.getBatchId());
    }

    /**
     * Removes wait tracking states for batches belonging to a specific runner in bulk.
     */
    static void removeWaitTrackingForRunner(EngineRuntimeState runtimeState, String runnerId) {
        if (runtimeState == null || runnerId == null || runnerId.isBlank()) {
            return;
        }
        String prefix = runnerId + "-batch-";
        removeWaitTrackingByPrefix(runtimeState.bindIpWaitStartByBatchId(), prefix);
        for (ResourceSlotGroup group : runtimeState.resourceSlotGroups().values()) {
            removeWaitTrackingByPrefix(group.attachmentWaitStartByBatchId(), prefix);
        }
    }

    private static void removeWaitTrackingForBatch(EngineRuntimeState runtimeState, String batchId) {
        if (runtimeState == null || batchId == null || batchId.isBlank()) {
            return;
        }
        runtimeState.bindIpWaitStartByBatchId().remove(batchId);
        for (ResourceSlotGroup group : runtimeState.resourceSlotGroups().values()) {
            group.attachmentWaitStartByBatchId().remove(batchId);
        }
    }

    private static void removeWaitTrackingByPrefix(Map<String, Long> waitTrackingMap, String prefix) {
        if (waitTrackingMap == null || prefix == null || prefix.isBlank()) {
            return;
        }
        waitTrackingMap.keySet().removeIf(batchId -> batchId != null && batchId.startsWith(prefix));
    }
}
