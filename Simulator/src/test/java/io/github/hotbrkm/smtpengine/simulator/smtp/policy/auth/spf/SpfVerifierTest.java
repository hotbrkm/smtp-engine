package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.DnsResolver;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.TestDnsResolver;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpfVerifierTest {

    @Test
    void shouldReturnPassWhenIpMatchesIp4Mechanism() throws Exception {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("example.com", DnsResolver.QueryStatus.SUCCESS,
                List.of("v=spf1 ip4:192.0.2.0/24 -all"));

        SpfVerifier verifier = new SpfVerifier(resolver);
        SpfResult result = verifier.verify("example.com", InetAddress.getByName("192.0.2.10"));

        assertThat(result).isEqualTo(SpfResult.PASS);
    }

    @Test
    void shouldReturnFailWhenAllMechanismIsNegative() throws Exception {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("example.com", DnsResolver.QueryStatus.SUCCESS,
                List.of("v=spf1 ip4:192.0.2.0/24 -all"));

        SpfVerifier verifier = new SpfVerifier(resolver);
        SpfResult result = verifier.verify("example.com", InetAddress.getByName("198.51.100.10"));

        assertThat(result).isEqualTo(SpfResult.FAIL);
    }

    @Test
    void shouldReturnTempErrorWhenDnsLookupIsTemporaryFailure() throws Exception {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("example.com", DnsResolver.QueryStatus.TEMP_ERROR, List.of());

        SpfVerifier verifier = new SpfVerifier(resolver);
        SpfResult result = verifier.verify("example.com", InetAddress.getByName("192.0.2.10"));

        assertThat(result).isEqualTo(SpfResult.TEMPERROR);
    }

    @Test
    void shouldReturnPassWhenIpMatchesIp6Mechanism() throws Exception {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("example.com", DnsResolver.QueryStatus.SUCCESS,
                List.of("v=spf1 ip6:2001:db8::/32 -all"));

        SpfVerifier verifier = new SpfVerifier(resolver);
        SpfResult result = verifier.verify("example.com", InetAddress.getByName("2001:db8::10"));

        assertThat(result).isEqualTo(SpfResult.PASS);
    }

    @Test
    void shouldReturnPassWhenAaaaRecordMatchesAMechanism() throws Exception {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("example.com", DnsResolver.QueryStatus.SUCCESS,
                List.of("v=spf1 a -all"));
        resolver.setA("example.com", DnsResolver.QueryStatus.NOT_FOUND, List.of());
        resolver.setAaaa("example.com", DnsResolver.QueryStatus.SUCCESS,
                List.of(InetAddress.getByName("2001:db8::20")));

        SpfVerifier verifier = new SpfVerifier(resolver);
        SpfResult result = verifier.verify("example.com", InetAddress.getByName("2001:db8::20"));

        assertThat(result).isEqualTo(SpfResult.PASS);
    }
}
