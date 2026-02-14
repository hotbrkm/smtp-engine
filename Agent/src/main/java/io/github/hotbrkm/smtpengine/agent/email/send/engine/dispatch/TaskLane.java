package io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;

/**
 * @deprecated Use {@link DispatchLane}.
 */
@Deprecated(since = "0.1.0")
public enum TaskLane {
    RT_FRESH(DispatchLane.REALTIME_FRESH),
    RT_RETRY(DispatchLane.REALTIME_RETRY),
    BA_FRESH(DispatchLane.BATCH_FRESH),
    BA_RETRY(DispatchLane.BATCH_RETRY);

    private final DispatchLane dispatchLane;

    TaskLane(DispatchLane dispatchLane) {
        this.dispatchLane = dispatchLane;
    }

    public DispatchLane toDispatchLane() {
        return dispatchLane;
    }

    public static DispatchLane toDispatchLane(TaskLane lane) {
        return lane == null ? null : lane.toDispatchLane();
    }

    public static TaskLane fromDispatchLane(DispatchLane lane) {
        if (lane == null) {
            return null;
        }
        return switch (lane) {
            case REALTIME_FRESH -> RT_FRESH;
            case REALTIME_RETRY -> RT_RETRY;
            case BATCH_FRESH -> BA_FRESH;
            case BATCH_RETRY -> BA_RETRY;
        };
    }

    public boolean isRealtime() {
        return dispatchLane.isRealtime();
    }

    public boolean isRetry() {
        return dispatchLane.isRetry();
    }

    public static TaskLane forFresh(ExecutionMode mode) {
        return mode == ExecutionMode.REALTIME ? RT_FRESH : BA_FRESH;
    }

    public static TaskLane forRetry(ExecutionMode mode) {
        return mode == ExecutionMode.REALTIME ? RT_RETRY : BA_RETRY;
    }
}
