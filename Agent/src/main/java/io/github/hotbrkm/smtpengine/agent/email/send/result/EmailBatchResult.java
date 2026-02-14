package io.github.hotbrkm.smtpengine.agent.email.send.result;

public record EmailBatchResult(String batchId, String domain, int totalTargets, int successCount, int failureCount,
                               boolean success, Exception exception) {

    /**
     * Creates a success result
     */
    public static EmailBatchResult success(String batchId, String domain, int totalTargets, int successCount) {
        return new EmailBatchResult(batchId, domain, totalTargets, successCount, totalTargets - successCount, true, null);
    }

    /**
     * Creates a failure result
     */
    public static EmailBatchResult failure(String batchId, String domain, int totalTargets, Exception exception) {
        return new EmailBatchResult(batchId, domain, totalTargets, 0, totalTargets, false, exception);
    }
}
