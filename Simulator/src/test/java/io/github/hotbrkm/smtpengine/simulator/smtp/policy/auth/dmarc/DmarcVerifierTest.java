package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dmarc;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.DnsResolver;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.TestDnsResolver;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf.SpfResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DmarcVerifierTest {

    @Test
    void shouldReturnNoneWhenRecordMissing() {
        DmarcVerifier verifier = new DmarcVerifier(new TestDnsResolver());

        DmarcResult result = verifier.evaluate("example.com", false, SpfResult.NONE, "example.com");

        assertThat(result.pass()).isTrue();
        assertThat(result.evaluation()).isEqualTo(DmarcResult.Evaluation.NONE);
    }

    @Test
    void shouldReturnRejectWhenPolicyRejectAndNoAlignment() {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("_dmarc.example.com", DnsResolver.QueryStatus.SUCCESS,
                List.of("v=DMARC1; p=reject"));

        DmarcVerifier verifier = new DmarcVerifier(resolver);
        DmarcResult result = verifier.evaluate("example.com", false, SpfResult.FAIL, "sender.other.com");

        assertThat(result.pass()).isFalse();
        assertThat(result.disposition()).isEqualTo(DmarcResult.Disposition.REJECT);
        assertThat(result.evaluation()).isEqualTo(DmarcResult.Evaluation.FAIL);
    }

    @Test
    void shouldReturnPassWhenSpfIsAligned() {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("_dmarc.example.com", DnsResolver.QueryStatus.SUCCESS,
                List.of("v=DMARC1; p=reject"));

        DmarcVerifier verifier = new DmarcVerifier(resolver);
        DmarcResult result = verifier.evaluate("example.com", false, SpfResult.PASS, "bounce.example.com");

        assertThat(result.pass()).isTrue();
        assertThat(result.evaluation()).isEqualTo(DmarcResult.Evaluation.PASS);
    }

    @Test
    void shouldReturnTempErrorWhenDnsLookupIsTemporaryFailure() {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("_dmarc.example.com", DnsResolver.QueryStatus.TEMP_ERROR, List.of());

        DmarcVerifier verifier = new DmarcVerifier(resolver);
        DmarcResult result = verifier.evaluate("example.com", false, SpfResult.NONE, "example.com");

        assertThat(result.pass()).isFalse();
        assertThat(result.evaluation()).isEqualTo(DmarcResult.Evaluation.TEMPERROR);
    }
}
