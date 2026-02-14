package io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendContext;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.ExecutionMode;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResult;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResultWriter;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Class representing a batch task.
 * Contains batch data and metadata.
 */
@Getter
public class DomainBatchTask {

    private final List<EmailSendTarget> batch;
    private final String domain;
    private final long createdTime;
    private final String batchId;
    private final CompletableFuture<EmailBatchResult> resultFuture;
    private final int retryCount;
    private final String runnerId;
    private final EmailBatchResultWriter resultWriter;
    private final EmailSendContext emailSendContext;
    private final ExecutionMode executionMode;
    private final DispatchLane dispatchLane;
    private final boolean hasAttachment;

    public DomainBatchTask(List<EmailSendTarget> batch, String domain, String batchId, CompletableFuture<EmailBatchResult> resultFuture,
                           int retryCount, String runnerId, EmailBatchResultWriter resultWriter, EmailSendContext emailSendContext,
                           ExecutionMode executionMode, DispatchLane lane) {
        this.batch = batch;
        this.domain = Objects.requireNonNull(domain);
        this.batchId = Objects.requireNonNull(batchId);
        this.resultFuture = resultFuture;
        this.retryCount = retryCount;
        this.runnerId = Objects.requireNonNull(runnerId);
        this.resultWriter = resultWriter;
        this.emailSendContext = emailSendContext;
        this.executionMode = executionMode;
        this.dispatchLane = lane != null ? lane : resolveLane(this.executionMode, retryCount);
        this.hasAttachment = resolveHasAttachment(batch);
        this.createdTime = System.currentTimeMillis();
    }

    public DomainBatchTask(List<EmailSendTarget> batch, String domain, String batchId, CompletableFuture<EmailBatchResult> resultFuture,
                           int retryCount, String runnerId, EmailBatchResultWriter resultWriter, EmailSendContext emailSendContext,
                           ExecutionMode executionMode, TaskLane lane) {
        this(batch, domain, batchId, resultFuture, retryCount, runnerId, resultWriter, emailSendContext,
                executionMode, TaskLane.toDispatchLane(lane));
    }

    /**
     * Returns the batch size.
     *
     * @return Batch size
     */
    public int getSize() {
        return batch != null ? batch.size() : 0;
    }

    /**
     * Returns the elapsed time (ms) since the batch was created.
     *
     * @return Elapsed time
     */
    public long getAge() {
        return System.currentTimeMillis() - createdTime;
    }

    /**
     * Returns whether attachments exist.
     */
    public boolean hasAttachment() {
        return hasAttachment;
    }

    /**
     * @deprecated Use {@link #getDispatchLane()}.
     */
    @Deprecated(since = "0.1.0")
    public TaskLane getLane() {
        return TaskLane.fromDispatchLane(dispatchLane);
    }

    @Override
    public String toString() {
        return String.format("BatchTask[domain=%s, size=%d, age=%dms, retryCount=%d, mode=%s, lane=%s]",
                domain, getSize(), getAge(), retryCount, executionMode, dispatchLane);
    }

    private static DispatchLane resolveLane(ExecutionMode mode, int retryCount) {
        if (retryCount > 0) {
            return DispatchLane.forRetry(mode);
        }
        return DispatchLane.forFresh(mode);
    }

    private static boolean resolveHasAttachment(List<EmailSendTarget> batch) {
        if (batch == null || batch.isEmpty()) {
            return false;
        }

        for (EmailSendTarget target : batch) {
            if (target == null || target.getAttachments() == null || target.getAttachments().isEmpty()) {
                continue;
            }
            return true;
        }

        return false;
    }
}
