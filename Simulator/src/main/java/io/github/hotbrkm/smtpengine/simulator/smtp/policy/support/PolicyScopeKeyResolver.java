package io.github.hotbrkm.smtpengine.simulator.smtp.policy.support;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.util.EmailUtil;
import io.github.hotbrkm.smtpengine.simulator.smtp.util.IpAddressUtil;
import org.subethamail.smtp.MessageContext;

import java.util.Locale;

public final class PolicyScopeKeyResolver {

    private PolicyScopeKeyResolver() {
    }

    public static String resolve(String scope, PolicyContext context) {
        String normalized = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        MessageContext messageContext = context.getMessageContext();
        String ip = IpAddressUtil.getRemoteIp(messageContext);
        String mailFromDomain = EmailUtil.extractDomain(context.getMailFrom());
        String rcptDomain = EmailUtil.extractDomain(context.getCurrentRecipient());

        return switch (normalized) {
            case "ip" -> nullToEmpty(ip);
            case "mail-from-domain" -> nullToEmpty(mailFromDomain);
            case "rcpt-domain" -> nullToEmpty(rcptDomain);
            case "ip+mail-from-domain" -> join(ip, mailFromDomain);
            default -> join(ip, mailFromDomain);
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append(nullToEmpty(values[i]));
        }
        return builder.toString();
    }
}
