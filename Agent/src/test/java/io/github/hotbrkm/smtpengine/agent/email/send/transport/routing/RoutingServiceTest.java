package io.github.hotbrkm.smtpengine.agent.email.send.transport.routing;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.dns.DnsClient;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.dns.DnsQueryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Verify RoutingService behavior")
class RoutingServiceTest {
    private static final String TEST_DNS_SERVER = "127.0.0.1";

    @Test
    @DisplayName("Should route to target server when simulator is enabled")
    void testResolveTargets_simulatorEnabled() {
        // Given
        EmailConfig cfg = new EmailConfig();
        cfg.getSend().setSimulatorEnabled(true);
        cfg.getSend().setSimulatorServer("127.0.0.1:2525");
        DnsClient dns = new DnsClient(List.of(TEST_DNS_SERVER));
        RoutingService sut = new RoutingService(cfg, dns);

        // When
        List<String> targets = sut.resolveTargets("gmail.com");

        // Then
        assertThat(targets).containsExactly("127.0.0.1:2525");
    }

    @Test
    @DisplayName("Should return IP literal as-is when input is IP literal")
    void testResolveTargets_ipLiteral() {
        // Given
        EmailConfig cfg = new EmailConfig();
        DnsClient dns = new DnsClient(List.of(TEST_DNS_SERVER));
        RoutingService sut = new RoutingService(cfg, dns);

        // When
        List<String> targets = sut.resolveTargets("192.168.0.10");

        // Then
        assertThat(targets).containsExactly("192.168.0.10");
    }

    @Test
    @DisplayName("Should apply fixed IP mapping first if configured")
    void testResolveTargets_fixedIpBeforeDns() {
        // Given
        EmailConfig cfg = new EmailConfig();
        cfg.getSend().setFixedIpOfDomain(Map.of("gmail.com", "1.2.3.4"));
        DnsClient dns = new DnsClient(List.of(TEST_DNS_SERVER));
        RoutingService sut = new RoutingService(cfg, dns);

        // When
        List<String> targets = sut.resolveTargets("gmail.com");

        // Then
        assertThat(targets).containsExactly("1.2.3.4");
    }

    @Test
    @DisplayName("Should throw exception for excluded domain")
    void testResolveTargets_excludedDomain() {
        // Given
        EmailConfig cfg = new EmailConfig();
        cfg.getSend().setExcludedDomain(List.of("blocked.com"));
        DnsClient dns = new DnsClient(List.of(TEST_DNS_SERVER));
        RoutingService sut = new RoutingService(cfg, dns);

        // When // Then
        assertThatThrownBy(() -> sut.resolveTargets("blocked.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Excluded Domain");
    }

    @Test
    @DisplayName("Should fallback to DNS lookup")
    void testResolveTargets_dnsFallback() {
        // Given
        EmailConfig cfg = new EmailConfig();
        StubDnsClient dns = new StubDnsClient(DnsQueryResult.success(List.of("10.0.0.1", "10.0.0.2")));
        RoutingService sut = new RoutingService(cfg, dns);

        // When
        List<String> targets = sut.resolveTargets("example.com");

        // Then
        assertThat(targets).containsExactly("10.0.0.1", "10.0.0.2");
    }

    // ===== Test Stub =====
    static class StubDnsClient extends DnsClient {
        private final DnsQueryResult result;

        StubDnsClient(DnsQueryResult result) {
            super(List.of(TEST_DNS_SERVER));
            this.result = result;
        }

        @Override
        public DnsQueryResult resolveDomainToIpAddresses(String domain) {
            return result;
        }
    }
}
