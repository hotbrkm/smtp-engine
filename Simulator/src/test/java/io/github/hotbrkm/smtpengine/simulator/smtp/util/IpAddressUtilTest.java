package io.github.hotbrkm.smtpengine.simulator.smtp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IpAddressUtil Test")
class IpAddressUtilTest {

    @Nested
    @DisplayName("ipv4ToInt() method")
    class Ipv4ToIntTest {

        @Test
        @DisplayName("Converts valid IPv4 address to integer")
        void shouldConvertValidIpToInt() {
            // Given
            String ip = "192.168.1.1";

            // When
            int result = IpAddressUtil.ipv4ToInt(ip);

            // Then
            // 192.168.1.1 = (192 << 24) | (168 << 16) | (1 << 8) | 1
            int expected = (192 << 24) | (168 << 16) | (1 << 8) | 1;
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("Converts 0.0.0.0 to 0")
        void shouldConvertZeroIp() {
            // Given
            String ip = "0.0.0.0";

            // When
            int result = IpAddressUtil.ipv4ToInt(ip);

            // Then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("Converts 255.255.255.255 to -1")
        void shouldConvertMaxIp() {
            // Given
            String ip = "255.255.255.255";

            // When
            int result = IpAddressUtil.ipv4ToInt(ip);

            // Then
            assertThat(result).isEqualTo(-1); // All bits set to 1 becomes -1 in signed int
        }
    }

    @Nested
    @DisplayName("cidrMatch() method")
    class CidrMatchTest {

        @Test
        @DisplayName("Matches IP within /24 subnet")
        void shouldMatchIpInSubnet24() {
            // Given
            String ip = "192.168.1.100";
            String cidr = "192.168.1.0/24";

            // When & Then
            assertThat(IpAddressUtil.cidrMatch(ip, cidr)).isTrue();
        }

        @Test
        @DisplayName("Does not match IP outside /24 subnet")
        void shouldNotMatchIpOutsideSubnet24() {
            // Given
            String ip = "192.168.2.1";
            String cidr = "192.168.1.0/24";

            // When & Then
            assertThat(IpAddressUtil.cidrMatch(ip, cidr)).isFalse();
        }

        @Test
        @DisplayName("Matches IP within /16 subnet")
        void shouldMatchIpInSubnet16() {
            // Given
            String ip = "10.20.30.40";
            String cidr = "10.20.0.0/16";

            // When & Then
            assertThat(IpAddressUtil.cidrMatch(ip, cidr)).isTrue();
        }

        @Test
        @DisplayName("/32 matches only exact IP")
        void shouldMatchExactIpWithPrefix32() {
            // Given
            String exactIp = "192.168.1.100";
            String cidr = "192.168.1.100/32";

            // When & Then
            assertThat(IpAddressUtil.cidrMatch(exactIp, cidr)).isTrue();
            assertThat(IpAddressUtil.cidrMatch("192.168.1.101", cidr)).isFalse();
        }

        @Test
        @DisplayName("/0 matches all IPs")
        void shouldMatchAllIpsWithPrefix0() {
            // Given
            String cidr = "0.0.0.0/0";

            // When & Then
            assertThat(IpAddressUtil.cidrMatch("192.168.1.1", cidr)).isTrue();
            assertThat(IpAddressUtil.cidrMatch("10.0.0.1", cidr)).isTrue();
            assertThat(IpAddressUtil.cidrMatch("255.255.255.255", cidr)).isTrue();
        }

        @Test
        @DisplayName("Returns false for invalid CIDR format")
        void shouldReturnFalseForInvalidCidr() {
            // Given
            String ip = "192.168.1.1";

            // When & Then
            assertThat(IpAddressUtil.cidrMatch(ip, "invalid")).isFalse();
            assertThat(IpAddressUtil.cidrMatch(ip, "192.168.1.0/abc")).isFalse();
        }
    }

    @Nested
    @DisplayName("matchesIpWhitelist() method")
    class MatchesIpWhitelistTest {

        @Test
        @DisplayName("Exact IP matching")
        void shouldMatchExactIp() {
            // Given
            String ip = "192.168.1.1";
            List<String> whitelist = Arrays.asList("192.168.1.1", "10.0.0.1");

            // When & Then
            assertThat(IpAddressUtil.matchesIpWhitelist(ip, whitelist)).isTrue();
        }

        @Test
        @DisplayName("CIDR range matching")
        void shouldMatchCidrRange() {
            // Given
            String ip = "192.168.1.50";
            List<String> whitelist = Arrays.asList("192.168.1.0/24");

            // When & Then
            assertThat(IpAddressUtil.matchesIpWhitelist(ip, whitelist)).isTrue();
        }

        @Test
        @DisplayName("Returns false for IP not in whitelist")
        void shouldNotMatchUnlistedIp() {
            // Given
            String ip = "192.168.1.1";
            List<String> whitelist = Arrays.asList("10.0.0.1", "172.16.0.0/12");

            // When & Then
            assertThat(IpAddressUtil.matchesIpWhitelist(ip, whitelist)).isFalse();
        }

        @Test
        @DisplayName("Returns false for null IP")
        void shouldReturnFalseForNullIp() {
            // Given
            List<String> whitelist = Arrays.asList("192.168.1.1");

            // When & Then
            assertThat(IpAddressUtil.matchesIpWhitelist(null, whitelist)).isFalse();
        }

        @Test
        @DisplayName("Returns false for empty whitelist")
        void shouldReturnFalseForEmptyWhitelist() {
            // Given
            String ip = "192.168.1.1";

            // When & Then
            assertThat(IpAddressUtil.matchesIpWhitelist(ip, Collections.emptyList())).isFalse();
        }

        @Test
        @DisplayName("Returns false for null whitelist")
        void shouldReturnFalseForNullWhitelist() {
            // Given
            String ip = "192.168.1.1";

            // When & Then
            assertThat(IpAddressUtil.matchesIpWhitelist(ip, null)).isFalse();
        }

        @Test
        @DisplayName("Ignores null/empty entries in whitelist")
        void shouldIgnoreNullAndEmptyEntries() {
            // Given
            String ip = "192.168.1.1";
            List<String> whitelist = Arrays.asList(null, "", "  ", "192.168.1.1");

            // When & Then
            assertThat(IpAddressUtil.matchesIpWhitelist(ip, whitelist)).isTrue();
        }
    }
}
