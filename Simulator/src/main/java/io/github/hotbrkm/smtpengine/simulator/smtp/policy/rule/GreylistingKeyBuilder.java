package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.util.IpAddressUtil;
import org.subethamail.smtp.MessageContext;

public class GreylistingKeyBuilder {

    private final String trackBy;

    public GreylistingKeyBuilder(String trackBy) {
        this.trackBy = trackBy;
    }

    public String buildKey(PolicyContext context) {
        MessageContext mc = context.getMessageContext();
        String ip = IpAddressUtil.getRemoteIp(mc);
        String from = nullToEmpty(context.getMailFrom());
        String rcpt = nullToEmpty(context.getCurrentRecipient());

        String mode = (trackBy == null) ? "ip-mail-from-rcpt-to" : trackBy.toLowerCase();

        return switch (mode) {
            case "ip" -> nullToEmpty(ip);
            case "mail-from" -> from;
            case "rcpt-to" -> rcpt;
            case "ip-mail-from" -> join(ip, from);
            case "ip-rcpt-to" -> join(ip, rcpt);
            case "mail-from-rcpt-to" -> join(from, rcpt);
            case "ip-mail-from-rcpt-to" -> join(ip, from, rcpt);
            default -> join(ip, from, rcpt);
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String join(String... parts) {
        return String.join("|", parts);
    }
}
