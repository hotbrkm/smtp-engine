package io.github.hotbrkm.smtpengine.agent.email.send.transport.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IpUtil behavior verification")
class IpUtilTest {

    @Test
    @DisplayName("IPv4 valid case detection")
    void testIsIpv4Literal_validAddresses() {
        // Given
        String a = "127.0.0.1";
        String b = "0.0.0.0";
        String c = "255.255.255.255";

        // When
        boolean ra = IpUtil.isIpv4Literal(a);
        boolean rb = IpUtil.isIpv4Literal(b);
        boolean rc = IpUtil.isIpv4Literal(c);

        // Then
        assertThat(ra).isTrue();
        assertThat(rb).isTrue();
        assertThat(rc).isTrue();
    }

    @Test
    @DisplayName("IPv4 boundary value detection")
    void testIsIpv4Literal_boundaryValues() {
        // Given
        String low = "0.0.0.1";
        String high = "1.2.3.255";

        // When
        boolean rl = IpUtil.isIpv4Literal(low);
        boolean rh = IpUtil.isIpv4Literal(high);

        // Then
        assertThat(rl).isTrue();
        assertThat(rh).isTrue();
    }

    @Test
    @DisplayName("IPv4 invalid case detection")
    void testIsIpv4Literal_invalidAddresses() {
        // Given
        String empty = "";
        String nullStr = null;
        String outOfRange = "256.1.1.1";
        String negative = "-1.1.1.1";
        String nonNumeric = "1.a.1.1";
        String tooFew = "1.1.1";
        String tooMany = "1.1.1.1.1";
        String trailingDot = "1.1.1.1.";

        // When // Then
        assertThat(IpUtil.isIpv4Literal(empty)).isFalse();
        assertThat(IpUtil.isIpv4Literal(nullStr)).isFalse();
        assertThat(IpUtil.isIpv4Literal(outOfRange)).isFalse();
        assertThat(IpUtil.isIpv4Literal(negative)).isFalse();
        assertThat(IpUtil.isIpv4Literal(nonNumeric)).isFalse();
        assertThat(IpUtil.isIpv4Literal(tooFew)).isFalse();
        assertThat(IpUtil.isIpv4Literal(tooMany)).isFalse();
        assertThat(IpUtil.isIpv4Literal(trailingDot)).isFalse();
    }

    @Test
    @DisplayName("IP literal detection delegation")
    void testIsIpLiteral_delegateToIpv4() {
        // Given
        String ip = "10.20.30.40";

        // When
        boolean result = IpUtil.isIpLiteral(ip);

        // Then
        assertThat(result).isTrue();
    }
}

