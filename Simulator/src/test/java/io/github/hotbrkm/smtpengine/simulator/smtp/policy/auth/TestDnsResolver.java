package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestDnsResolver extends DnsResolver {

    private final Map<String, QueryResult> txtLookup = new HashMap<>();
    private final Map<String, List<String>> txtValues = new HashMap<>();

    private final Map<String, QueryResult> aLookup = new HashMap<>();
    private final Map<String, List<InetAddress>> aValues = new HashMap<>();
    private final Map<String, QueryResult> aaaaLookup = new HashMap<>();
    private final Map<String, List<InetAddress>> aaaaValues = new HashMap<>();

    private final Map<String, QueryResult> mxLookup = new HashMap<>();
    private final Map<String, List<String>> mxValues = new HashMap<>();

    public void setTxt(String name, QueryStatus status, List<String> values) {
        txtLookup.put(name, new QueryResult(status, List.of(), null));
        txtValues.put(name, values);
    }

    public void setA(String name, QueryStatus status, List<InetAddress> values) {
        aLookup.put(name, new QueryResult(status, List.of(), null));
        aValues.put(name, values);
    }

    public void setAaaa(String name, QueryStatus status, List<InetAddress> values) {
        aaaaLookup.put(name, new QueryResult(status, List.of(), null));
        aaaaValues.put(name, values);
    }

    public void setMx(String name, QueryStatus status, List<String> values) {
        mxLookup.put(name, new QueryResult(status, List.of(), null));
        mxValues.put(name, values);
    }

    @Override
    public QueryResult txtLookup(String name) {
        return txtLookup.getOrDefault(name, new QueryResult(QueryStatus.NOT_FOUND, List.of(), null));
    }

    @Override
    public QueryResult aLookup(String name) {
        return aLookup.getOrDefault(name, new QueryResult(QueryStatus.NOT_FOUND, List.of(), null));
    }

    @Override
    public QueryResult aaaaLookup(String name) {
        return aaaaLookup.getOrDefault(name, new QueryResult(QueryStatus.NOT_FOUND, List.of(), null));
    }

    @Override
    public QueryResult mxLookup(String name) {
        return mxLookup.getOrDefault(name, new QueryResult(QueryStatus.NOT_FOUND, List.of(), null));
    }

    @Override
    public List<String> txt(String name) {
        return txtValues.getOrDefault(name, List.of());
    }

    @Override
    public List<InetAddress> a(String name) {
        return aValues.getOrDefault(name, List.of());
    }

    @Override
    public List<InetAddress> aaaa(String name) {
        return aaaaValues.getOrDefault(name, List.of());
    }

    @Override
    public List<String> mxHosts(String domain) {
        return mxValues.getOrDefault(domain, List.of());
    }
}
