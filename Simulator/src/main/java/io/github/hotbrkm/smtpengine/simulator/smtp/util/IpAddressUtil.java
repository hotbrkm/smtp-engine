package io.github.hotbrkm.smtpengine.simulator.smtp.util;

import org.subethamail.smtp.MessageContext;

import java.net.InetSocketAddress;

/**
 * Utility class for IP address operations.
 * <p>
 * Provides functionality such as IPv4 address parsing and CIDR matching.
 */
public final class IpAddressUtil {

    private IpAddressUtil() {
        // Prevent instantiation
    }

    /**
     * Extracts the remote IP address from MessageContext.
     *
     * @param mc SMTP message context
     * @return IP address string, or null if extraction fails
     */
    public static String getRemoteIp(MessageContext mc) {
        try {
            InetSocketAddress remote = (InetSocketAddress) mc.getRemoteAddress();
            return remote.getAddress().getHostAddress();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Checks if an IPv4 address matches the whitelist.
     * <p>
     * Whitelist entries support single IP or CIDR notation.
     *
     * @param ip        IP address to check
     * @param whitelist List of whitelisted addresses/blocks
     * @return true if matched
     */
    public static boolean matchesIpWhitelist(String ip, Iterable<String> whitelist) {
        if (ip == null || whitelist == null) {
            return false;
        }
        for (String entry : whitelist) {
            if (entry == null) continue;
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.contains("/")) {
                if (cidrMatch(ip, trimmed)) return true;
            } else if (ip.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an IPv4 address belongs to a CIDR block.
     * <p>
     * Note: IPv6 is not supported.
     *
     * @param ip   IPv4 address to check (e.g. "192.168.1.100")
     * @param cidr CIDR block (e.g. "192.168.1.0/24")
     * @return true if matched
     */
    public static boolean cidrMatch(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String base = parts[0];
            int prefix = Integer.parseInt(parts[1]);
            int ipInt = ipv4ToInt(ip);
            int baseInt = ipv4ToInt(base);
            int mask = prefix == 0 ? 0 : -(1 << (32 - prefix));
            return (ipInt & mask) == (baseInt & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Converts an IPv4 address string to an integer.
     *
     * @param ip IPv4 address (e.g. "192.168.1.1")
     * @return Integer representation
     * @throws NumberFormatException if the IP format is invalid
     */
    public static int ipv4ToInt(String ip) {
        String[] octets = ip.split("\\.");
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | Integer.parseInt(octets[i]);
        }
        return result;
    }
}
