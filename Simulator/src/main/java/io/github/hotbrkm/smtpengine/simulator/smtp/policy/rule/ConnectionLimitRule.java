package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyReason;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.util.IpAddressUtil;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class ConnectionLimitRule implements PolicyRule {

    private static final int DEFAULT_CODE = 421;
    private static final String DEFAULT_MESSAGE = "4.7.0 Too many connections, try again later.";

    private final SimulatorSmtpProperties.PolicySet.ConnectionLimit config;

    private final Object lock = new Object();
    private final Map<String, Integer> activeByIp = new HashMap<>();
    private final Map<String, String> sessionToIp = new HashMap<>();

    @Override
    public boolean supports(SmtpPhase phase) {
        return phase == SmtpPhase.CONNECT_PRE;
    }

    @Override
    public PolicyOutcome evaluate(PolicyContext context) {
        if (config == null) {
            return PolicyOutcome.allow();
        }

        String sessionId = context.getMessageContext() == null ? null : context.getMessageContext().getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return PolicyOutcome.allow();
        }

        String ip = IpAddressUtil.getRemoteIp(context.getMessageContext());
        if (ip == null) {
            ip = "unknown";
        }

        synchronized (lock) {
            int globalMax = config.getGlobalMaxConnections() == null ? 0 : config.getGlobalMaxConnections();
            int perIpMax = config.getPerIpMaxConnections() == null ? 0 : config.getPerIpMaxConnections();

            int currentGlobal = sessionToIp.size();
            int currentIp = activeByIp.getOrDefault(ip, 0);

            if (globalMax > 0 && currentGlobal >= globalMax) {
                return onExceed("GLOBAL_LIMIT", globalMax);
            }
            if (perIpMax > 0 && currentIp >= perIpMax) {
                return onExceed("IP_LIMIT", perIpMax);
            }

            sessionToIp.put(sessionId, ip);
            activeByIp.put(ip, currentIp + 1);
        }

        return PolicyOutcome.allow();
    }

    @Override
    public void onSessionEnd(PolicyContext context) {
        if (context == null || context.getMessageContext() == null) {
            return;
        }
        String sessionId = context.getMessageContext().getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        synchronized (lock) {
            String ip = sessionToIp.remove(sessionId);
            if (ip == null) {
                return;
            }
            int count = activeByIp.getOrDefault(ip, 0);
            if (count <= 1) {
                activeByIp.remove(ip);
            } else {
                activeByIp.put(ip, count - 1);
            }
        }
    }

    private PolicyOutcome onExceed(String reason, int threshold) {
        int code = DEFAULT_CODE;
        String message = DEFAULT_MESSAGE;
        boolean disconnect = true;

        SimulatorSmtpProperties.PolicySet.Reply onExceed = config.getOnExceed();
        if (onExceed != null) {
            if (onExceed.getCode() != null) {
                code = onExceed.getCode();
            }
            if (onExceed.getMessage() != null && !onExceed.getMessage().isBlank()) {
                message = onExceed.getMessage();
            }
            if (onExceed.getDisconnect() != null) {
                disconnect = onExceed.getDisconnect();
            }
        }

        message = message + " [reason=" + reason + ",threshold=" + threshold + "]";
        if (disconnect) {
            return PolicyOutcome.disconnect(code, message, PolicyReason.CONNECTION_LIMIT);
        }
        if (code >= 500) {
            return PolicyOutcome.permFail(code, message, PolicyReason.CONNECTION_LIMIT);
        }
        return PolicyOutcome.tempFail(code, message, PolicyReason.CONNECTION_LIMIT);
    }
}
