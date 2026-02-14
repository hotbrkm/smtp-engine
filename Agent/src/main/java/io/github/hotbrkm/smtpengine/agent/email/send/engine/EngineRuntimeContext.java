package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.dispatch.DomainBatchQueue;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.metrics.DomainSendMetrics;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpSessionAllocator;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpCooldownPolicy;

/**
 * Context holding runtime dependencies required for engine execution.
 */
record EngineRuntimeContext(EmailConfig.Send sendConfig,
                            EngineRuntimeOptions runtimeOptions,
                            DomainBatchQueue batchQueue,
                            BindIpSessionAllocator bindIpSessionAllocator,
                            EngineRuntimeState runtimeState,
                            RunnerExecutionGuard runnerExecutionGuard,
                            BatchResultFinalizer batchResultFinalizer,
                            BatchSubmissionService batchSubmissionService,
                            EngineExecutors engineExecutors,
                            int bindIpAllocationTimeoutCode,
                            long noSlotRequeueBaseDelayMs,
                            long noSlotRequeueJitterMs,
                            DomainSendMetrics domainSendMetrics) {

    /**
     * Initializes runtime components and creates the context.
     */
    static EngineRuntimeContext initialize(EmailConfig.Send sendConfig,
                                           EngineRuntimeOptions runtimeOptions,
                                           EmailDomainManager emailDomainManager,
                                           int bindIpAllocationTimeoutCode,
                                           long noSlotRequeueBaseDelayMs,
                                           long noSlotRequeueJitterMs) {
        EmailConfig.Send requiredSendConfig = Objects.requireNonNull(sendConfig, "sendConfig must not be null");
        EngineRuntimeOptions requiredOptions = Objects.requireNonNull(runtimeOptions, "runtimeOptions must not be null");

        DomainBatchQueue batchQueue = new DomainBatchQueue();
        BindIpSessionAllocator bindIpSessionAllocator = getBindIpSessionAllocator(emailDomainManager, requiredOptions, requiredSendConfig);
        EngineRuntimeState runtimeState = new EngineRuntimeState(requiredOptions);
        RunnerExecutionGuard runnerExecutionGuard = new RunnerExecutionGuard(batchQueue);
        BatchResultFinalizer batchResultFinalizer = new BatchResultFinalizer(runtimeState,
                task -> WaitTrackingSupport.removeWaitTrackingForTask(runtimeState, task));
        EngineExecutors engineExecutors = new EngineExecutors(requiredOptions.workerCount());
        DomainSendMetrics domainSendMetrics = new DomainSendMetrics(60, 60);
        BatchSubmissionService batchSubmissionService = new BatchSubmissionService(runnerExecutionGuard, batchQueue, domainSendMetrics);

        return new EngineRuntimeContext(requiredSendConfig, requiredOptions, batchQueue, bindIpSessionAllocator, runtimeState,
                runnerExecutionGuard, batchResultFinalizer, batchSubmissionService, engineExecutors,
                bindIpAllocationTimeoutCode, noSlotRequeueBaseDelayMs, noSlotRequeueJitterMs, domainSendMetrics);
    }

    private static @NonNull BindIpSessionAllocator getBindIpSessionAllocator(EmailDomainManager emailDomainManager,
                                                                             EngineRuntimeOptions requiredOptions,
                                                                             EmailConfig.Send requiredSendConfig) {
        EmailDomainManager requiredEmailDomainManager = Objects.requireNonNull(emailDomainManager, "emailDomainManager must not be null");
        BindIpCooldownPolicy bindIpCooldownPolicy = new BindIpCooldownPolicy(requiredOptions.bindIpCooldownTriggerCodes(),
                requiredSendConfig::getCooldownThresholdForCode, requiredSendConfig.getBindIpFailureCooldownMs());
        return new BindIpSessionAllocator(requiredOptions.bindIps(), requiredEmailDomainManager, bindIpCooldownPolicy);
    }
}
