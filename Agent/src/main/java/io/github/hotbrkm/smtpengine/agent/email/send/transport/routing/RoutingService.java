package io.github.hotbrkm.smtpengine.agent.email.send.transport.routing;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.dns.DnsClient;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.dns.DnsQueryResult;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.network.IpUtil;


import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Routing service that determines transport target (IP or host:port) candidates for a domain.
 * Policy priority:
 * 1) Simulator forced routing
 * 2) Use IP literal input as-is
 * 3) Fixed IP mapping (fixedIpOfDomain)
 * 4) Block excluded domains (excludedDomain)
 * 5) DNS lookup (MX → A)
 */
public class RoutingService {
    private final EmailConfig.Send sendConfig;
    private final DnsClient dnsClient;

    public RoutingService(EmailConfig emailConfig) {
        this(emailConfig, createDnsClient(emailConfig));
    }

    public RoutingService(EmailConfig emailConfig, DnsClient dnsClient) {
        this.sendConfig = requireSendConfig(emailConfig);
        this.dnsClient = Objects.requireNonNull(dnsClient, "dnsClient must not be null");
    }

    /**
     * Returns transport target candidates for a domain.
     * Returned strings can be IPv4 literals ("1.2.3.4") or "host:port" format.
     */
    public List<String> resolveTargets(String domain) {
        if (domain == null || domain.isBlank()) {
            return Collections.emptyList();
        }

        // 1) Simulator forced
        if (sendConfig.isSimulatorEnabled() && sendConfig.getSimulatorServer() != null) {
            return Collections.singletonList(sendConfig.getSimulatorServer());
        }

        // 2) Use IP literal as-is
        if (IpUtil.isIpLiteral(domain)) {
            return Collections.singletonList(domain);
        }

        // 3) Fixed IP mapping
        Map<String, String> fixed = sendConfig.getFixedIpOfDomain();
        String fixedIp = (fixed == null) ? null : fixed.get(domain.toLowerCase(Locale.ROOT));
        if (fixedIp != null) {
            return Collections.singletonList(fixedIp);
        }

        // 4) Block excluded domain
        List<String> excluded = sendConfig.getExcludedDomain();
        if (excluded != null && excluded.contains(domain.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Excluded Domain");
        }

        // 5) DNS lookup (MX → A)
        DnsQueryResult queryResult = dnsClient.resolveDomainToIpAddresses(domain);
        return queryResult.getRecords();
    }



    private static DnsClient createDnsClient(EmailConfig emailConfig) {
        return new DnsClient(requireSendConfig(emailConfig).getDnsServer());
    }

    private static EmailConfig.Send requireSendConfig(EmailConfig emailConfig) {
        if (emailConfig == null || emailConfig.getSend() == null) {
            throw new IllegalArgumentException("EmailConfig.Send must not be null");
        }
        return emailConfig.getSend();
    }
}
