package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.DnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpfVerifier {

    private static final int MAX_INCLUDE_DEPTH = 5;

    private final DnsResolver dnsResolver;

    public SpfVerifier() {
        this(new DnsResolver());
    }

    public SpfVerifier(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    public SpfResult verify(String domain, InetAddress ip) {
        if (domain == null || domain.isBlank()) {
            return SpfResult.NONE;
        }

        if (ip == null) {
            return SpfResult.NEUTRAL;
        }

        return verify(domain.trim().toLowerCase(Locale.ROOT), ip, 0, new ArrayDeque<>());
    }

    private SpfResult verify(String domain, InetAddress ip, int depth, ArrayDeque<String> chain) {
        if (depth > MAX_INCLUDE_DEPTH) {
            return SpfResult.PERMERROR;
        }
        if (chain.contains(domain)) {
            return SpfResult.PERMERROR;
        }

        chain.push(domain);
        try {
            DnsResolver.QueryResult result = dnsResolver.txtLookup(domain);
            if (result.status() == DnsResolver.QueryStatus.TEMP_ERROR) {
                return SpfResult.TEMPERROR;
            }
            if (result.status() == DnsResolver.QueryStatus.PERM_ERROR) {
                return SpfResult.PERMERROR;
            }

            List<String> spfRecords = dnsResolver.txt(domain).stream()
                    .map(value -> value == null ? "" : value.trim())
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith("v=spf1"))
                    .toList();

            if (spfRecords.isEmpty()) {
                return SpfResult.NONE;
            }
            if (spfRecords.size() > 1) {
                return SpfResult.PERMERROR;
            }

            String[] tokens = spfRecords.get(0).split("\\s+");
            for (int i = 1; i < tokens.length; i++) {
                String token = tokens[i].trim();
                if (token.isEmpty()) {
                    continue;
                }

                char qualifier = '+';
                char first = token.charAt(0);
                if (first == '+' || first == '-' || first == '~' || first == '?') {
                    qualifier = first;
                    token = token.substring(1);
                }

                if (token.isEmpty()) {
                    continue;
                }

                SpfResult eval = evaluateMechanism(domain, token, ip, depth, chain);
                if (eval == SpfResult.TEMPERROR || eval == SpfResult.PERMERROR) {
                    return eval;
                }
                if (eval == SpfResult.PASS) {
                    return qualifierToResult(qualifier);
                }
            }

            return SpfResult.NEUTRAL;
        } finally {
            chain.pop();
        }
    }

    private SpfResult evaluateMechanism(String domain, String mechanism, InetAddress ip, int depth, ArrayDeque<String> chain) {
        String lower = mechanism.toLowerCase(Locale.ROOT);

        if ("all".equals(lower)) {
            return SpfResult.PASS;
        }

        if (lower.startsWith("ip4:")) {
            String cidr = mechanism.substring(4).trim();
            if (cidr.isEmpty()) {
                return SpfResult.PERMERROR;
            }
            return matchesIpv4(ip, cidr) ? SpfResult.PASS : SpfResult.NEUTRAL;
        }

        if (lower.startsWith("ip6:")) {
            String cidr = mechanism.substring(4).trim();
            if (cidr.isEmpty()) {
                return SpfResult.PERMERROR;
            }
            return matchesIpv6(ip, cidr) ? SpfResult.PASS : SpfResult.NEUTRAL;
        }

        if (lower.startsWith("a") && (lower.length() == 1 || lower.charAt(1) == ':')) {
            String host = lower.length() == 1 ? domain : mechanism.substring(2).trim();
            if (host.isEmpty()) {
                host = domain;
            }
            DnsResolver.QueryResult query = dnsResolver.aLookup(host);
            if (query.status() == DnsResolver.QueryStatus.TEMP_ERROR) {
                return SpfResult.TEMPERROR;
            }
            if (query.status() == DnsResolver.QueryStatus.PERM_ERROR) {
                return SpfResult.PERMERROR;
            }

            DnsResolver.QueryResult aaaaQuery = dnsResolver.aaaaLookup(host);
            if (aaaaQuery.status() == DnsResolver.QueryStatus.TEMP_ERROR) {
                return SpfResult.TEMPERROR;
            }
            if (aaaaQuery.status() == DnsResolver.QueryStatus.PERM_ERROR) {
                return SpfResult.PERMERROR;
            }

            return matchAnyAddress(host, ip) ? SpfResult.PASS : SpfResult.NEUTRAL;
        }

        if (lower.startsWith("mx") && (lower.length() == 2 || lower.charAt(2) == ':')) {
            String mxDomain = lower.length() == 2 ? domain : mechanism.substring(3).trim();
            if (mxDomain.isEmpty()) {
                mxDomain = domain;
            }
            DnsResolver.QueryResult mxResult = dnsResolver.mxLookup(mxDomain);
            if (mxResult.status() == DnsResolver.QueryStatus.TEMP_ERROR) {
                return SpfResult.TEMPERROR;
            }
            if (mxResult.status() == DnsResolver.QueryStatus.PERM_ERROR) {
                return SpfResult.PERMERROR;
            }

            List<String> hosts = dnsResolver.mxHosts(mxDomain);
            for (String host : hosts) {
                DnsResolver.QueryResult aResult = dnsResolver.aLookup(host);
                if (aResult.status() == DnsResolver.QueryStatus.TEMP_ERROR) {
                    return SpfResult.TEMPERROR;
                }
                if (aResult.status() == DnsResolver.QueryStatus.PERM_ERROR) {
                    return SpfResult.PERMERROR;
                }

                DnsResolver.QueryResult aaaaResult = dnsResolver.aaaaLookup(host);
                if (aaaaResult.status() == DnsResolver.QueryStatus.TEMP_ERROR) {
                    return SpfResult.TEMPERROR;
                }
                if (aaaaResult.status() == DnsResolver.QueryStatus.PERM_ERROR) {
                    return SpfResult.PERMERROR;
                }

                if (matchAnyAddress(host, ip)) {
                    return SpfResult.PASS;
                }
            }
            return SpfResult.NEUTRAL;
        }

        if (lower.startsWith("include:")) {
            String includeDomain = mechanism.substring(8).trim().toLowerCase(Locale.ROOT);
            if (includeDomain.isEmpty()) {
                return SpfResult.PERMERROR;
            }
            SpfResult includeResult = verify(includeDomain, ip, depth + 1, chain);
            if (includeResult == SpfResult.PASS) {
                return SpfResult.PASS;
            }
            if (includeResult == SpfResult.TEMPERROR || includeResult == SpfResult.PERMERROR) {
                return includeResult;
            }
            return SpfResult.NEUTRAL;
        }

        return SpfResult.NEUTRAL;
    }

    private SpfResult qualifierToResult(char qualifier) {
        return switch (qualifier) {
            case '-' -> SpfResult.FAIL;
            case '~' -> SpfResult.SOFTFAIL;
            case '?' -> SpfResult.NEUTRAL;
            default -> SpfResult.PASS;
        };
    }

    private boolean matchAnyAddress(String host, InetAddress ip) {
        if (ip == null) {
            return false;
        }
        List<InetAddress> candidates = new ArrayList<>(dnsResolver.a(host));
        candidates.addAll(dnsResolver.aaaa(host));
        return candidates.stream().anyMatch(addr -> addr.equals(ip));
    }

    private boolean matchesIpv4(InetAddress ip, String cidrText) {
        if (!(ip.getAddress().length == 4)) {
            return false;
        }

        String[] parts = cidrText.split("/", 2);
        String baseIp = parts[0];
        int prefix = 32;
        if (parts.length == 2) {
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (prefix < 0 || prefix > 32) {
                return false;
            }
        }

        byte[] base;
        try {
            base = InetAddress.getByName(baseIp).getAddress();
        } catch (UnknownHostException e) {
            return false;
        }
        if (base.length != 4) {
            return false;
        }

        int mask = prefix == 0 ? 0 : (-1 << (32 - prefix));
        int ipInt = toInt(ip.getAddress());
        int baseInt = toInt(base);
        return (ipInt & mask) == (baseInt & mask);
    }

    private boolean matchesIpv6(InetAddress ip, String cidrText) {
        if (!(ip.getAddress().length == 16)) {
            return false;
        }

        String[] parts = cidrText.split("/", 2);
        String baseIp = parts[0];
        int prefix = 128;
        if (parts.length == 2) {
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (prefix < 0 || prefix > 128) {
                return false;
            }
        }

        byte[] base;
        try {
            base = InetAddress.getByName(baseIp).getAddress();
        } catch (UnknownHostException e) {
            return false;
        }
        if (base.length != 16) {
            return false;
        }

        byte[] candidate = ip.getAddress();
        int fullBytes = prefix / 8;
        int remainingBits = prefix % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != base[i]) {
                return false;
            }
        }

        if (remainingBits == 0) {
            return true;
        }

        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (candidate[fullBytes] & mask) == (base[fullBytes] & mask);
    }

    private int toInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }
}
