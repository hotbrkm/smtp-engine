package io.github.hotbrkm.smtpengine.agent.email.send.transport.network;

/**
 * IP detection utility class.
 * Currently provides IPv4 literal detection only, extensible as needed.
 */
public final class IpUtil {

    private IpUtil() {
    }

    /**
     * Determines if the input is an IPv4 literal (e.g., 192.168.0.1).
     * - Leading zeros allowed (e.g., 0.0.0.0)
     * - Each octet range: 0~255
     * - Trailing dot (.) not allowed
     *
     * @param input string to check
     * @return true if IPv4 literal, false otherwise
     */
    public static boolean isIpv4Literal(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // Fast filter: check if only allowed characters are present
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c < '0' || c > '9') && c != '.') {
                return false;
            }
        }

        // Trailing dot (.) not allowed
        if (input.charAt(input.length() - 1) == '.') {
            return false;
        }

        String[] parts = input.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            if (part.isEmpty()) {
                return false;
            }
            // Check 0~255 range
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return false;
            }
            if (value < 0 || value > 255) {
                return false;
            }
        }

        return true;
    }

    /**
     * Determines if the input is an IP literal.
     * Currently only IPv4 is supported.
     *
     * @param input string to check
     * @return true if IP (IPv4) literal
     */
    public static boolean isIpLiteral(String input) {
        return isIpv4Literal(input);
    }
}

