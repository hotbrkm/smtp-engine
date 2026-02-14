package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dkim;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.DnsResolver;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.TestDnsResolver;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DkimVerifierTest {

    @Test
    void shouldReturnNoneWhenSignatureHeaderMissing() {
        DkimVerifier verifier = new DkimVerifier(new TestDnsResolver());

        DkimVerifier.Result result = verifier.verify(Map.of("From", "a@example.com"), new byte[0]);

        assertThat(result.result()).isEqualTo(DkimResult.NONE);
    }

    @Test
    void shouldReturnPassWhenSignatureAndDnsKeyArePresent() {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("selector._domainkey.example.com", DnsResolver.QueryStatus.SUCCESS,
                List.of("v=DKIM1; p=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A"));

        DkimVerifier verifier = new DkimVerifier(resolver);
        Map<String, String> headers = new HashMap<>();
        headers.put("DKIM-Signature", "v=1; a=rsa-sha256; d=example.com; s=selector; bh=abc; b=def;");

        DkimVerifier.Result result = verifier.verify(headers, new byte[0]);

        assertThat(result.result()).isEqualTo(DkimResult.PASS);
        assertThat(result.domain()).isEqualTo("example.com");
        assertThat(result.selector()).isEqualTo("selector");
    }

    @Test
    void shouldReturnTempErrorWhenDnsLookupIsTemporaryFailure() {
        TestDnsResolver resolver = new TestDnsResolver();
        resolver.setTxt("selector._domainkey.example.com", DnsResolver.QueryStatus.TEMP_ERROR, List.of());

        DkimVerifier verifier = new DkimVerifier(resolver);
        Map<String, String> headers = new HashMap<>();
        headers.put("DKIM-Signature", "v=1; a=rsa-sha256; d=example.com; s=selector; bh=abc; b=def;");

        DkimVerifier.Result result = verifier.verify(headers, new byte[0]);

        assertThat(result.result()).isEqualTo(DkimResult.TEMPERROR);
    }
}
