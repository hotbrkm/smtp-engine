package io.github.hotbrkm.smtpengine.agent.email.send.transport.dns;

public enum DnsQueryStatus {
    SUCCESS, UNRECOVERABLE, TRY_AGAIN, HOST_NOT_FOUND, TYPE_NOT_FOUND, EMPTY_RECORD, UNKNOWN;

    public static DnsQueryStatus of(int statusCode) {
        switch (statusCode) {
            case -1:
                return EMPTY_RECORD;
            case 0:
                return SUCCESS;
            case 1:
                return UNRECOVERABLE;
            case 2:
                return TRY_AGAIN;
            case 3:
                return HOST_NOT_FOUND;
            case 4:
                return TYPE_NOT_FOUND;
            default:
                return UNKNOWN;
        }
    }
}
