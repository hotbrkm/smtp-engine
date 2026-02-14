package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stores resource slot counters and wait tracking states per execution mode (REALTIME/BATCH).
 */
final class ResourceSlotGroup {

    private final int attachmentMaxInFlight;
    private final AtomicInteger attachmentInFlight = new AtomicInteger(0);
    private final Map<String, Long> attachmentWaitStartByBatchId = new ConcurrentHashMap<>();

    /**
     * Normalizes the max in-flight value to 0 or greater to prevent negative inputs.
     */
    ResourceSlotGroup(int attachmentMaxInFlight) {
        this.attachmentMaxInFlight = Math.max(0, attachmentMaxInFlight);
    }

    int attachmentMaxInFlight() {
        return attachmentMaxInFlight;
    }

    AtomicInteger attachmentInFlight() {
        return attachmentInFlight;
    }

    Map<String, Long> attachmentWaitStartByBatchId() {
        return attachmentWaitStartByBatchId;
    }
}
