package io.github.hotbrkm.smtpengine.simulator.smtp.handler;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicyOrchestrator;
import io.github.hotbrkm.smtpengine.simulator.smtp.service.SmtpMessageStore;
import lombok.RequiredArgsConstructor;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;

@RequiredArgsConstructor
public class SimulatorMessageHandlerFactory implements MessageHandlerFactory {

    private final SimulatorSmtpProperties properties;
    private final SmtpMessageStore messageStore;
    private final PolicyOrchestrator policyOrchestrator;

    @Override
    public MessageHandler create(MessageContext context) {
        return new SimulatorMessageHandler(context, properties, messageStore, policyOrchestrator);
    }
}
