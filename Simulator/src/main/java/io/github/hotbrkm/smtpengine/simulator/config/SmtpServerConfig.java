package io.github.hotbrkm.smtpengine.simulator.config;

import io.github.hotbrkm.smtpengine.simulator.smtp.handler.SimulatorMessageHandlerFactory;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.metrics.PolicyMetricsRecorder;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicyOrchestrator;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicyOrchestratorFactory;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicySessionHandler;
import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.service.SmtpMessageStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.subethamail.smtp.server.SMTPServer;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@Configuration
@EnableConfigurationProperties(SimulatorSmtpProperties.class)
public class SmtpServerConfig {

    private static final String PROPERTY_PREFIX = "simulator.smtp";

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = PROPERTY_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    public SMTPServer smtpServer(SimulatorSmtpProperties properties,
                                 SimulatorMessageHandlerFactory handlerFactory,
                                 PolicyOrchestrator policyOrchestrator) {
        SMTPServer.Builder builder = new SMTPServer.Builder()
                .messageHandlerFactory(handlerFactory)
                .sessionHandler(new PolicySessionHandler(policyOrchestrator))
                .port(properties.getPort());

        if (properties.getMaxConnections() != null) {
            builder.maxConnections(properties.getMaxConnections());
        }

        if (properties.getMaxMessageSize() != null) {
            builder.maxMessageSize(properties.getMaxMessageSize());
        }

        if (StringUtils.hasText(properties.getHostName())) {
            builder.hostName(properties.getHostName());
        }

        if (StringUtils.hasText(properties.getBindAddress())) {
            builder.bindAddress(resolveBindAddress(properties.getBindAddress()));
        }

        SMTPServer smtpServer = builder.build();
        log.info("Simulator SMTP server is configured to listen on {}.", smtpServer.getDisplayableLocalSocketAddress());
        return smtpServer;
    }

    @Bean
    @ConditionalOnProperty(prefix = PROPERTY_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    public PolicyOrchestrator policyOrchestrator(SimulatorSmtpProperties properties,
                                                 PolicyMetricsRecorder metricsRecorder) {
        return PolicyOrchestratorFactory.build(properties.getPolicy(), metricsRecorder);
    }

    @Bean
    @ConditionalOnProperty(prefix = PROPERTY_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    public SimulatorMessageHandlerFactory simulatorMessageHandlerFactory(SimulatorSmtpProperties properties,
                                                                         SmtpMessageStore messageStore,
                                                                         PolicyOrchestrator policyOrchestrator) {
        return new SimulatorMessageHandlerFactory(properties, messageStore, policyOrchestrator);
    }

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnProperty(prefix = PROPERTY_PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
    public PolicyMetricsRecorder policyMetricsRecorder(MeterRegistry meterRegistry) {
        return new PolicyMetricsRecorder(meterRegistry);
    }

    private static InetAddress resolveBindAddress(String bindAddress) {
        try {
            return InetAddress.getByName(bindAddress);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Invalid SMTP bind address: " + bindAddress, e);
        }
    }
}
