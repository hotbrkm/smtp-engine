package io.github.hotbrkm.smtpengine.agent.email.send.transport.dns;

import io.github.hotbrkm.smtpengine.agent.email.send.transport.network.IpUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
@Setter
@Slf4j
public class DnsClient {

    private static final String A_RECORD = "A";
    private static final String MX_RECORD = "MX";

    private final List<String> dnsServerArray;
    private int retryCount = 3;

    public DnsClient(List<String> dnsServerArray) {
        if (dnsServerArray == null || dnsServerArray.isEmpty()) {
            throw new IllegalArgumentException("dnsServerArray must not be null or empty");
        }

        List<String> sanitizedServers = dnsServerArray.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (sanitizedServers.isEmpty()) {
            throw new IllegalArgumentException("dnsServerArray must contain at least one valid DNS server");
        }

        this.dnsServerArray = List.copyOf(sanitizedServers);
    }

    public DnsQueryResult resolveDomainToIpAddresses(String domain) {
        if (domain == null || domain.isBlank()) {
            return getEmptyRecordResult("600 DNS.error. domain is empty");
        }

        try {
            DnsQueryResult mxQueryResult = queryMxRecords(domain);

            if (mxQueryResult.isSuccess()) {
                List<String> ipAddresses = resolveMxRecordsToIpAddresses(mxQueryResult.getRecords());
                return getDnsQueryResult(domain, ipAddresses);
            } else if (mxQueryResult.isTypeNotFound()) {
                return queryARecords(domain);
            }

            return mxQueryResult;
        } catch (Exception ex) {
            return getEmptyRecordResult("600 DNS.error.. " + ex.getMessage());
        }
    }

    private DnsQueryResult getDnsQueryResult(String domain, List<String> ipAddresses) {
        if (ipAddresses.isEmpty()) {
            return getEmptyRecordResult(getErrorMessage(-1, domain, A_RECORD));
        }

        return getSuccessResult(ipAddresses);
    }

    private DnsQueryResult getSuccessResult(List<String> ipAddresses) {
        return DnsQueryResult.success(ipAddresses);
    }

    private DnsQueryResult getEmptyRecordResult(String message) {
        return DnsQueryResult.emptyRecord(message);
    }

    public DnsQueryResult queryMxRecords(String domain) throws TextParseException, UnknownHostException {
        if (domain == null || domain.isBlank()) {
            return getEmptyRecordResult("600 DNS.error. domain is empty");
        }

        for (String dnsServer : dnsServerArray) {
            for (int i = 0; i < retryCount; i++) {
                Lookup lookup = new Lookup(domain, Type.MX);
                try {
                    lookup.setResolver(new SimpleResolver(dnsServer));
                } catch (UnknownHostException e) {
                    log.warn("Invalid DNS server host for MX query. dnsServer={}, domain={}", dnsServer, domain, e);
                    break;
                }
                lookup.run();
                int result = lookup.getResult();

                DnsQueryStatus status = DnsQueryStatus.of(result);
                String errorMessage = getErrorMessage(result, domain, MX_RECORD);
                List<String> records = extractMxAddresses(lookup);
                DnsQueryResult queryResult = new DnsQueryResult(status, errorMessage, records);

                if (queryResult.isSuccess() || queryResult.isTypeNotFound()) {
                    return queryResult;
                }

                if (isNotTryAgain(result)) {
                    break;
                }
            }
        }

        return getEmptyRecordResult(getErrorMessage(-1, domain, MX_RECORD));
    }

    private boolean isNotTryAgain(int result) {
        return Lookup.TRY_AGAIN != result;
    }

    private String getErrorMessage(int resultCode, String domain, String type) {
        switch (resultCode) {
            case -1:
                return "600 DNS.query failure No Records Found. Domain: " + domain + ", Type: " + type;
            case Lookup.SUCCESSFUL:
                return "SUCCESS";
            case Lookup.TRY_AGAIN:
                return "600 DNS.query failure Try Again. Domain: " + domain + ", Type: " + type;
            case Lookup.HOST_NOT_FOUND:
                return "610 DNS.query failure UnknownHost. Domain: " + domain + ", Type: " + type;
            case Lookup.UNRECOVERABLE:
                return "600 DNS.query failure UNRECOVERABLE. Domain: " + domain + ", Type: " + type;
            case Lookup.TYPE_NOT_FOUND:
                return "600 DNS.query failure Type Not Found. Domain: " + domain + ", Type: " + type;
            default:
                return "600 DNS.query failure Code: " + resultCode + ", Domain: " + domain + ", Type: " + type;
        }
    }

    private List<String> extractMxAddresses(Lookup lookup) {
        if (lookup.getAnswers() == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(lookup.getAnswers())
                .map(MXRecord.class::cast)
                .sorted(Comparator.comparingInt(MXRecord::getPriority))
                .map(it -> it.getTarget().toString())
                .collect(Collectors.toList());
    }

    private List<String> resolveMxRecordsToIpAddresses(List<String> mxRecords) throws TextParseException, UnknownHostException {
        List<String> list = new ArrayList<>();

        for (String mxRecord : mxRecords) {
            if (IpUtil.isIpv4Literal(mxRecord)) {
                list.add(mxRecord);
            } else {
                DnsQueryResult queryResult = queryARecords(mxRecord);

                if (queryResult.isSuccess()) {
                    list.addAll(queryResult.getRecords());
                }
            }
        }

        return list;
    }

    public DnsQueryResult queryARecords(String domain) throws TextParseException, UnknownHostException {
        if (domain == null || domain.isBlank()) {
            return getEmptyRecordResult("600 DNS.error. domain is empty");
        }

        for (String dnsServer : dnsServerArray) {
            for (int i = 0; i < retryCount; i++) {
                Lookup lookup = new Lookup(domain, Type.A);
                try {
                    lookup.setResolver(new SimpleResolver(dnsServer));
                } catch (UnknownHostException e) {
                    log.warn("Invalid DNS server host for A query. dnsServer={}, domain={}", dnsServer, domain, e);
                    break;
                }
                lookup.run();
                int result = lookup.getResult();

                DnsQueryStatus status = DnsQueryStatus.of(result);
                String errorMessage = getErrorMessage(result, domain, A_RECORD);
                List<String> ipAddresses = extractIpAddresses(lookup);
                DnsQueryResult queryResult = new DnsQueryResult(status, errorMessage, ipAddresses);

                if (queryResult.isSuccess()) {
                    return queryResult;
                }

                if (isNotTryAgain(result)) {
                    break;
                }
            }
        }

        return getEmptyRecordResult(getErrorMessage(-1, domain, A_RECORD));
    }

    private List<String> extractIpAddresses(Lookup lookup) {
        if (lookup.getAnswers() == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(lookup.getAnswers())
                .map(it -> {
                    InetAddress address = ((ARecord) it).getAddress();
                    String hostAddress = address.getHostAddress();

                    if (hostAddress.contains(":")) {
                        int index = hostAddress.indexOf(":");
                        if (index == 0) {
                            return "localhost";
                        } else {
                            return hostAddress.substring(0, index);
                        }
                    } else {
                        return hostAddress;
                    }
                })
                .collect(Collectors.toList());
    }
}
