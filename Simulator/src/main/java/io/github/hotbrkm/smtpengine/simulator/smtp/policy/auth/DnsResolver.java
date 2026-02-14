package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth;

import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class DnsResolver {

    public QueryResult txtLookup(String name) {
        return lookup(name, Type.TXT);
    }

    public QueryResult aLookup(String name) {
        return lookup(name, Type.A);
    }

    public QueryResult aaaaLookup(String name) {
        return lookup(name, Type.AAAA);
    }

    public QueryResult mxLookup(String name) {
        return lookup(name, Type.MX);
    }

    public List<String> txt(String name) {
        QueryResult result = txtLookup(name);
        if (result.status() != QueryStatus.SUCCESS) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        for (Record record : result.records()) {
            if (record instanceof TXTRecord txt) {
                StringBuilder sb = new StringBuilder();
                for (Object s : txt.getStrings()) {
                    if (!sb.isEmpty()) {
                        sb.append(' ');
                    }
                    sb.append(s);
                }
                values.add(sb.toString());
            }
        }
        return values;
    }

    public List<InetAddress> a(String name) {
        QueryResult result = aLookup(name);
        if (result.status() != QueryStatus.SUCCESS) {
            return Collections.emptyList();
        }

        List<InetAddress> values = new ArrayList<>();
        for (Record record : result.records()) {
            if (record instanceof ARecord a) {
                values.add(a.getAddress());
            }
        }
        return values;
    }

    public List<InetAddress> aaaa(String name) {
        QueryResult result = aaaaLookup(name);
        if (result.status() != QueryStatus.SUCCESS) {
            return Collections.emptyList();
        }

        List<InetAddress> values = new ArrayList<>();
        for (Record record : result.records()) {
            if (record instanceof AAAARecord aaaa) {
                values.add(aaaa.getAddress());
            }
        }
        return values;
    }

    public List<String> mxHosts(String domain) {
        QueryResult result = mxLookup(domain);
        if (result.status() != QueryStatus.SUCCESS) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        for (Record record : result.records()) {
            if (record instanceof MXRecord mx) {
                values.add(mx.getTarget().toString(true));
            }
        }
        return values;
    }

    private QueryResult lookup(String name, int type) {
        try {
            Lookup lookup = new Lookup(name, type);
            Record[] records = lookup.run();
            QueryStatus status = mapStatus(lookup.getResult());
            if (records == null) {
                records = new Record[0];
            }
            return new QueryResult(status, List.of(records), lookup.getErrorString());
        } catch (TextParseException e) {
            log.debug("DNS lookup parse error: name={}, type={}, message={}", name, type, e.getMessage());
            return new QueryResult(QueryStatus.PERM_ERROR, Collections.emptyList(), e.getMessage());
        } catch (RuntimeException e) {
            log.debug("DNS lookup runtime error: name={}, type={}, message={}", name, type, e.getMessage());
            return new QueryResult(QueryStatus.TEMP_ERROR, Collections.emptyList(), e.getMessage());
        }
    }

    private QueryStatus mapStatus(int result) {
        return switch (result) {
            case Lookup.SUCCESSFUL -> QueryStatus.SUCCESS;
            case Lookup.HOST_NOT_FOUND, Lookup.TYPE_NOT_FOUND -> QueryStatus.NOT_FOUND;
            case Lookup.TRY_AGAIN -> QueryStatus.TEMP_ERROR;
            case Lookup.UNRECOVERABLE -> QueryStatus.PERM_ERROR;
            default -> QueryStatus.PERM_ERROR;
        };
    }

    public enum QueryStatus {
        SUCCESS,
        NOT_FOUND,
        TEMP_ERROR,
        PERM_ERROR
    }

    public record QueryResult(QueryStatus status, List<Record> records, String detail) {
    }
}
