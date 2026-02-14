package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.worker.EmailBatchSenderFactory;
import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import lombok.experimental.UtilityClass;

import java.util.Objects;

/**
 * Factory dedicated to assembling EmailSendEngine.
 */
@UtilityClass
public class EmailSendEngineFactory {

    static final int BIND_IP_ALLOCATION_TIMEOUT_CODE = 705;
    static final long DEFAULT_NO_SLOT_REQUEUE_BASE_DELAY_MS = 200L;
    static final long DEFAULT_NO_SLOT_REQUEUE_JITTER_MS = 100L;

    /**
     * Creates a default engine based on configuration.
     */
    public static EmailSendEngine create(EmailConfig.Send sendConfig, EmailDomainManager emailDomainManager, EmailBatchSenderFactory emailBatchSenderFactory) {
        EmailConfig.Send effectiveSendConfig = Objects.requireNonNull(sendConfig, "sendConfig must not be null");
        EngineRuntimeOptions options = EngineRuntimeOptions.fromSendConfig(effectiveSendConfig);
        Assembly assemble = assemble(emailBatchSenderFactory, emailDomainManager, effectiveSendConfig, options);
        return new EmailSendEngine(assemble);
    }

    /**
     * Creates an engine by explicitly specifying runtime parameters.
     */
    public static EmailSendEngine create(int maxConcurrentWorkers, EmailBatchSenderFactory emailBatchSenderFactory,
                                         EmailDomainManager emailDomainManager, EmailConfig.Send sendConfig,
                                         int schedulerIntervalMs, int maxRetryCount) {
        return create(maxConcurrentWorkers, emailBatchSenderFactory, emailDomainManager, sendConfig, schedulerIntervalMs, maxRetryCount,
                EmailConfig.Send.DEFAULT_BATCH_INITIAL_RETRY_DELAY_MS, EmailConfig.Send.DEFAULT_BATCH_MAX_RETRY_DELAY_MS,
                EmailConfig.Send.DEFAULT_BATCH_RETRY_BACKOFF_MULTIPLIER);
    }

    /**
     * Creates an engine including retry backoff parameters.
     */
    public static EmailSendEngine create(int maxConcurrentWorkers, EmailBatchSenderFactory emailBatchSenderFactory,
                                         EmailDomainManager emailDomainManager, EmailConfig.Send sendConfig,
                                         int schedulerIntervalMs, int maxRetryCount, long initialRetryDelayMillis,
                                         long maxRetryDelayMillis, double retryBackoffMultiplier) {
        EmailConfig.Send effectiveSendConfig = Objects.requireNonNull(sendConfig, "sendConfig must not be null");
        EngineRuntimeOptions options = EngineRuntimeOptions.fromExplicit(effectiveSendConfig, maxConcurrentWorkers,
                schedulerIntervalMs, maxRetryCount, initialRetryDelayMillis, maxRetryDelayMillis, retryBackoffMultiplier);
        Assembly assemble = assemble(emailBatchSenderFactory, emailDomainManager, effectiveSendConfig, options);
        return new EmailSendEngine(assemble);
    }

    /**
     * Assembles runtime components and returns the engine Assembly.
     */
    static Assembly assemble(EmailBatchSenderFactory emailBatchSenderFactory, EmailDomainManager emailDomainManager,
                             EmailConfig.Send sendConfig, EngineRuntimeOptions options) {
        Objects.requireNonNull(emailBatchSenderFactory, "emailBatchSenderFactory must not be null");
        Objects.requireNonNull(emailDomainManager, "emailDomainManager must not be null");

        EmailConfig.Send requiredSendConfig = Objects.requireNonNull(sendConfig, "sendConfig must not be null");
        EngineRuntimeOptions runtimeOptions = Objects.requireNonNull(options, "options must not be null");

        EngineRuntimeContext context = EngineRuntimeContext.initialize(requiredSendConfig, runtimeOptions,
                emailDomainManager, BIND_IP_ALLOCATION_TIMEOUT_CODE,
                DEFAULT_NO_SLOT_REQUEUE_BASE_DELAY_MS, DEFAULT_NO_SLOT_REQUEUE_JITTER_MS);

        emailBatchSenderFactory.setDomainSendMetrics(context.domainSendMetrics());

        RetryScheduler retryScheduler = new RetryScheduler(context);
        ResourceGate resourceGate = new ResourceGate(context, retryScheduler);
        ExecutionCoordinator executionCoordinator = new ExecutionCoordinator(
                context, emailBatchSenderFactory, retryScheduler, resourceGate);
        DispatchProcessor dispatchProcessor = new DispatchProcessor(context, executionCoordinator::executeBatch);

        return new Assembly(context, executionCoordinator, dispatchProcessor);
    }

    /**
     * Assembly result object for engine configuration.
     */
    record Assembly(EngineRuntimeContext context, ExecutionCoordinator executionCoordinator, DispatchProcessor dispatchProcessor) {
    }
}
