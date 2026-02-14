package io.github.hotbrkm.smtpengine.agent.email.send.transport.dns;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class DnsQueryResult {

    private final DnsQueryStatus status;
    private final String message;
    private final List<String> records;

    public DnsQueryResult(DnsQueryStatus status, String message) {
        this.status = status;
        this.message = message;
        this.records = Collections.emptyList();
    }

    public DnsQueryResult(DnsQueryStatus status, String message, List<String> records) {
        this.status = status;
        this.message = message;
        this.records = records;
    }

    public static DnsQueryResult emptyRecord(String message) {
        return new DnsQueryResult(DnsQueryStatus.EMPTY_RECORD, message);
    }

    public static DnsQueryResult success(List<String> records) {
        return new DnsQueryResult(DnsQueryStatus.SUCCESS, "SUCCESS", records);
    }

    public boolean isSuccess() {
        return status.equals(DnsQueryStatus.SUCCESS);
    }

    public boolean isTypeNotFound() {
        return status.equals(DnsQueryStatus.TYPE_NOT_FOUND);
    }
}