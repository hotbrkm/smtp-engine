package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyReason;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.util.EmailUtil;
import lombok.RequiredArgsConstructor;

import java.net.InetSocketAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
public class DisconnectRule implements PolicyRule {

    private static final int DEFAULT_CODE = 421;
    private static final String DEFAULT_MESSAGE = "4.7.0 Session closed due to policy.";

    private final SimulatorSmtpProperties.PolicySet.Disconnect config;

    @Override
    public boolean supports(SmtpPhase phase) {
        return phase == SmtpPhase.RCPT_PRE;
    }

    @Override
    public PolicyOutcome evaluate(PolicyContext context) {
        if (config == null || config.getConditions() == null) {
            return PolicyOutcome.allow();
        }

        if (!matches(context)) {
            return PolicyOutcome.allow();
        }

        int code = DEFAULT_CODE;
        String message = DEFAULT_MESSAGE;
        if (config.getReply() != null) {
            if (config.getReply().getCode() != null) {
                code = config.getReply().getCode();
            }
            if (config.getReply().getMessage() != null && !config.getReply().getMessage().isBlank()) {
                message = config.getReply().getMessage();
            }
        }

        if (config.isCloseAfterReply()) {
            return PolicyOutcome.disconnect(code, message, PolicyReason.DISCONNECT_RULE);
        }
        if (code >= 500) {
            return PolicyOutcome.permFail(code, message, PolicyReason.DISCONNECT_RULE);
        }
        return PolicyOutcome.tempFail(code, message, PolicyReason.DISCONNECT_RULE);
    }

    private boolean matches(PolicyContext context) {
        SimulatorSmtpProperties.PolicySet.Disconnect.DisconnectConditions conditions = config.getConditions();

        if (conditions.getBlockedDomains() != null && !conditions.getBlockedDomains().isEmpty()) {
            String recipientDomain = EmailUtil.extractDomain(context.getCurrentRecipient());
            if (recipientDomain != null) {
                for (String blockedDomain : conditions.getBlockedDomains()) {
                    if (recipientDomain.equalsIgnoreCase(blockedDomain)) {
                        return true;
                    }
                }
            }
        }

        if (conditions.getBlockedIps() != null && !conditions.getBlockedIps().isEmpty()
                && context.getMessageContext() != null
                && context.getMessageContext().getRemoteAddress() instanceof InetSocketAddress remote) {
            String remoteIp = remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
            if (remoteIp != null && conditions.getBlockedIps().contains(remoteIp)) {
                return true;
            }
        }

        if (conditions.getTimeWindows() != null && !conditions.getTimeWindows().isEmpty()) {
            LocalTime now = LocalTime.now();
            for (SimulatorSmtpProperties.PolicySet.TimeWindow window : conditions.getTimeWindows()) {
                if (window == null || window.getStart() == null || window.getEnd() == null) {
                    continue;
                }
                if (isInWindow(now, window)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isInWindow(LocalTime now, SimulatorSmtpProperties.PolicySet.TimeWindow window) {
        LocalTime start = LocalTime.parse(window.getStart(), DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime end = LocalTime.parse(window.getEnd(), DateTimeFormatter.ofPattern("HH:mm"));

        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }
}
