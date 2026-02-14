package io.github.hotbrkm.smtpengine.agent.email.domain;

public final class EmailAddressUtil {
    /**
     * Label used when email/domain is determined to be invalid
     */
    public static final String INVALID = "INVALID";

    private EmailAddressUtil() {}

    public static String extractDomain(String email) {
        if (email == null) {
            return INVALID;
        }
        String addr = email.trim();
        if (addr.isEmpty()) {
            return INVALID;
        }

        int lt = addr.indexOf('<');
        int gt = addr.indexOf('>');
        if (lt >= 0 && gt > lt) {
            addr = addr.substring(lt + 1, gt).trim();
        }
        if (addr.startsWith("\"") && addr.endsWith("\"") && addr.length() >= 2) {
            addr = addr.substring(1, addr.length() - 1).trim();
        }

        int at = addr.lastIndexOf('@');
        if (at <= 0 || at >= addr.length() - 1) {
            return INVALID;
        }
        String dom = addr.substring(at + 1).trim();
        if (dom.isEmpty() || dom.charAt(0) == '[') {
            return INVALID;
        }
        if (dom.endsWith(".")) {
            dom = dom.substring(0, dom.length() - 1);
        }

        String asciiDom;
        try {
            asciiDom = java.net.IDN.toASCII(dom);
        } catch (Exception e) {
            return INVALID;
        }

        if (asciiDom.indexOf(',') != -1 || asciiDom.indexOf('"') != -1 || asciiDom.indexOf('\'') != -1 || asciiDom.indexOf('<') != -1 || asciiDom.indexOf('>') != -1
                || asciiDom.indexOf('\\') != -1 || asciiDom.indexOf('/') != -1 || asciiDom.indexOf(' ') != -1 || asciiDom.indexOf(':') != -1) {
            return INVALID;
        }
        if (asciiDom.length() <= 2) {
            return INVALID;
        }

        return asciiDom.toLowerCase();
    }
}
