package io.github.hotbrkm.smtpengine.simulator.smtp.util;

/**
 * Utility class for email address operations.
 */
public final class EmailUtil {

    private EmailUtil() {
        // Prevent instantiation
    }

    /**
     * Extracts the domain part from an email address.
     *
     * @param address Email address (e.g. user@example.com)
     * @return Domain part (lowercase), or null if invalid
     */
    public static String extractDomain(String address) {
        if (address == null) {
            return null;
        }
        int at = address.indexOf('@');
        if (at < 0 || at == address.length() - 1) {
            return null;
        }
        return address.substring(at + 1).toLowerCase();
    }
}
