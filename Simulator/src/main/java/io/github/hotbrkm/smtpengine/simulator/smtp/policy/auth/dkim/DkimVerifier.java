package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dkim;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.DnsResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DkimVerifier {

    private final DnsResolver dnsResolver;

    public DkimVerifier() {
        this(new DnsResolver());
    }

    public DkimVerifier(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    public Result verify(Map<String, String> headers, byte[] rawMessage) {
        String dkimSignature = findHeader(headers, "DKIM-Signature");
        if (dkimSignature == null || dkimSignature.isBlank()) {
            return new Result(DkimResult.NONE, null, null);
        }

        Map<String, String> tags = parseTags(dkimSignature);
        String domain = normalizeTag(tags.get("d"));
        String selector = normalizeTag(tags.get("s"));
        String b = normalizeTag(tags.get("b"));
        String bh = normalizeTag(tags.get("bh"));

        if (domain == null || selector == null || b == null || bh == null) {
            return new Result(DkimResult.PERMERROR, domain, selector);
        }

        String dnsName = selector + "._domainkey." + domain;
        DnsResolver.QueryResult result = dnsResolver.txtLookup(dnsName);
        if (result.status() == DnsResolver.QueryStatus.TEMP_ERROR) {
            return new Result(DkimResult.TEMPERROR, domain, selector);
        }
        if (result.status() == DnsResolver.QueryStatus.PERM_ERROR) {
            return new Result(DkimResult.PERMERROR, domain, selector);
        }

        List<String> txtRecords = dnsResolver.txt(dnsName);
        if (txtRecords.isEmpty()) {
            return new Result(DkimResult.FAIL, domain, selector);
        }

        String keyRecord = txtRecords.stream()
                .map(v -> v == null ? "" : v.trim())
                .filter(v -> !v.isBlank())
                .findFirst()
                .orElse("");

        if (keyRecord.isBlank()) {
            return new Result(DkimResult.FAIL, domain, selector);
        }

        Map<String, String> keyTags = parseTags(keyRecord);
        String version = keyTags.getOrDefault("v", "DKIM1");
        String p = normalizeTag(keyTags.get("p"));
        if (!"dkim1".equalsIgnoreCase(version) || p == null || p.isBlank()) {
            return new Result(DkimResult.FAIL, domain, selector);
        }

        return new Result(DkimResult.PASS, domain, selector);
    }

    private String findHeader(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, String> parseTags(String value) {
        Map<String, String> map = new HashMap<>();
        if (value == null || value.isBlank()) {
            return map;
        }

        String[] parts = value.split(";");
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int index = token.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = token.substring(0, index).trim().toLowerCase(Locale.ROOT);
            String val = token.substring(index + 1).trim();
            if (!key.isEmpty()) {
                map.put(key, val);
            }
        }
        return map;
    }

    private String normalizeTag(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record Result(DkimResult result, String domain, String selector) {
    }
}
