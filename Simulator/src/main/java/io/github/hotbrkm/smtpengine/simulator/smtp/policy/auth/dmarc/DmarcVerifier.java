package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dmarc;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.DnsResolver;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf.SpfResult;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DmarcVerifier {

    private final DnsResolver dnsResolver;

    public DmarcVerifier() {
        this(new DnsResolver());
    }

    public DmarcVerifier(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    public DmarcResult evaluate(String fromDomain, boolean dkimPassAligned, SpfResult spfResult, String spfDomain) {
        if (fromDomain == null || fromDomain.isBlank()) {
            return new DmarcResult(true, DmarcResult.Disposition.NONE, null, DmarcResult.Evaluation.NONE);
        }

        String queryName = "_dmarc." + fromDomain.toLowerCase(Locale.ROOT).trim();
        DnsResolver.QueryResult result = dnsResolver.txtLookup(queryName);

        if (result.status() == DnsResolver.QueryStatus.TEMP_ERROR) {
            return new DmarcResult(false, DmarcResult.Disposition.NONE, fromDomain, DmarcResult.Evaluation.TEMPERROR);
        }
        if (result.status() == DnsResolver.QueryStatus.PERM_ERROR) {
            return new DmarcResult(false, DmarcResult.Disposition.NONE, fromDomain, DmarcResult.Evaluation.PERMERROR);
        }

        List<String> records = dnsResolver.txt(queryName).stream()
                .map(v -> v == null ? "" : v.trim())
                .filter(v -> v.toLowerCase(Locale.ROOT).startsWith("v=dmarc1"))
                .toList();

        if (records.isEmpty()) {
            return new DmarcResult(true, DmarcResult.Disposition.NONE, fromDomain, DmarcResult.Evaluation.NONE);
        }

        if (records.size() > 1) {
            return new DmarcResult(false, DmarcResult.Disposition.NONE, fromDomain, DmarcResult.Evaluation.PERMERROR);
        }

        Map<String, String> tags = parseTags(records.get(0));
        String policy = normalize(tags.get("p"));
        if (policy == null) {
            return new DmarcResult(false, DmarcResult.Disposition.NONE, fromDomain, DmarcResult.Evaluation.PERMERROR);
        }

        boolean spfAligned = (spfResult == SpfResult.PASS) && alignedRelaxed(fromDomain, spfDomain);

        if (dkimPassAligned || spfAligned) {
            return new DmarcResult(true, DmarcResult.Disposition.NONE, fromDomain, DmarcResult.Evaluation.PASS);
        }

        DmarcResult.Disposition disposition = switch (policy.toLowerCase(Locale.ROOT)) {
            case "reject" -> DmarcResult.Disposition.REJECT;
            case "quarantine" -> DmarcResult.Disposition.QUARANTINE;
            default -> DmarcResult.Disposition.NONE;
        };

        if (disposition == DmarcResult.Disposition.NONE) {
            return new DmarcResult(true, disposition, fromDomain, DmarcResult.Evaluation.NONE);
        }
        return new DmarcResult(false, disposition, fromDomain, DmarcResult.Evaluation.FAIL);
    }

    private boolean alignedRelaxed(String fromDomain, String spfDomain) {
        if (fromDomain == null || spfDomain == null) {
            return false;
        }
        String from = fromDomain.toLowerCase(Locale.ROOT).trim();
        String spf = spfDomain.toLowerCase(Locale.ROOT).trim();

        if (from.equals(spf)) {
            return true;
        }
        return from.endsWith("." + spf) || spf.endsWith("." + from);
    }

    private Map<String, String> parseTags(String record) {
        Map<String, String> map = new HashMap<>();
        if (record == null || record.isBlank()) {
            return map;
        }
        String[] tokens = record.split(";");
        for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            int idx = t.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = t.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            String value = t.substring(idx + 1).trim();
            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }
        return map;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
