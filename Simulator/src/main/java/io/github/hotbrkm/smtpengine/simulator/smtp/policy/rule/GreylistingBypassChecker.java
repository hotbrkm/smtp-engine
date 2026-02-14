package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.util.EmailUtil;
import io.github.hotbrkm.smtpengine.simulator.smtp.util.IpAddressUtil;
import org.subethamail.smtp.MessageContext;

import java.util.List;
import java.util.Objects;

public class GreylistingBypassChecker {

    private final SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass;

    public GreylistingBypassChecker(SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass) {
        this.bypass = bypass;
    }

    public boolean shouldBypass(PolicyContext context) {
        if (bypass == null) {
            return false;
        }

        MessageContext mc = context.getMessageContext();

        if (isAuthenticatedSession(mc)) {
            return true;
        }
        if (isWhitelistedIp(mc)) {
            return true;
        }
        if (isWhitelistedSender(context.getMailFrom())) {
            return true;
        }
        return isWhitelistedRecipient(context.getCurrentRecipient());
    }

    private boolean isAuthenticatedSession(MessageContext mc) {
        if (!Boolean.TRUE.equals(bypass.getAuthenticated()) || mc == null) {
            return false;
        }
        try {
            return mc.getAuthenticationHandler().isPresent();
        } catch (Throwable ignore) {
            return false;
        }
    }

    private boolean isWhitelistedIp(MessageContext mc) {
        List<String> whitelistedIps = bypass.getWhitelistedIps();
        if (whitelistedIps == null || whitelistedIps.isEmpty()) {
            return false;
        }
        String ip = IpAddressUtil.getRemoteIp(mc);
        return ip != null && IpAddressUtil.matchesIpWhitelist(ip, whitelistedIps);
    }

    private boolean isWhitelistedSender(String from) {
        List<String> whitelistedSenders = bypass.getWhitelistedSenders();
        if (whitelistedSenders == null || whitelistedSenders.isEmpty()) {
            return false;
        }
        return from != null && matchesAddressOrDomain(from, whitelistedSenders);
    }

    private boolean isWhitelistedRecipient(String rcpt) {
        List<String> whitelistedRecipients = bypass.getWhitelistedRecipients();
        if (whitelistedRecipients == null || whitelistedRecipients.isEmpty()) {
            return false;
        }
        return rcpt != null && matchesAddressOrDomain(rcpt, whitelistedRecipients);
    }

    private boolean matchesAddressOrDomain(String address, List<String> patterns) {
        String lower = address.toLowerCase();
        String domain = EmailUtil.extractDomain(lower);

        for (String pattern : patterns) {
            if (pattern == null) {
                continue;
            }
            String trimmed = pattern.toLowerCase().trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("@")) {
                String patternDomain = trimmed.substring(1);
                if (Objects.equals(domain, patternDomain)) {
                    return true;
                }
            } else if (lower.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
