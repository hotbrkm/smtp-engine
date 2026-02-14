package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dmarc;

public record DmarcResult(boolean pass, Disposition disposition, String policyDomain, Evaluation evaluation) {

    public enum Disposition {
        NONE,
        QUARANTINE,
        REJECT
    }

    public enum Evaluation {
        PASS,
        NONE,
        TEMPERROR,
        PERMERROR,
        FAIL
    }
}
