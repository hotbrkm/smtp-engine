package io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dkim.DkimResult;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dkim.DkimVerifier;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dmarc.DmarcResult;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dmarc.DmarcVerifier;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf.SpfResult;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf.SpfVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Real DNS authentication record integration test")
class RealDnsAuthenticationIntegrationTest {

    private static final String DOMAIN = "gmail.com";
    private static final String SELECTOR = "20230601";

    @DisplayName("Looks up gmail.com SPF, DMARC, DKIM TXT records from actual DNS")
    @Test
    void testLookupAuthenticationTxtRecordsFromLiveDns() {
        DnsResolver resolver = new DnsResolver();

        DnsResolver.QueryResult spfLookup = resolver.txtLookup(DOMAIN);
        assertThat(spfLookup.status()).isEqualTo(DnsResolver.QueryStatus.SUCCESS);
        assertThat(resolver.txt(DOMAIN).stream().map(this::lowercase).toList())
                .anyMatch(value -> value.startsWith("v=spf1"));

        String dmarcName = "_dmarc." + DOMAIN;
        DnsResolver.QueryResult dmarcLookup = resolver.txtLookup(dmarcName);
        assertThat(dmarcLookup.status()).isEqualTo(DnsResolver.QueryStatus.SUCCESS);
        assertThat(resolver.txt(dmarcName).stream().map(this::lowercase).toList())
                .anyMatch(value -> value.startsWith("v=dmarc1"));

        String dkimName = SELECTOR + "._domainkey." + DOMAIN;
        DnsResolver.QueryResult dkimLookup = resolver.txtLookup(dkimName);
        assertThat(dkimLookup.status()).isEqualTo(DnsResolver.QueryStatus.SUCCESS);
        assertThat(resolver.txt(dkimName).stream().map(this::lowercase).toList())
                .anyMatch(value -> value.contains("v=dkim1") && value.contains("p="));
    }

    @DisplayName("DKIM verification result should be PASS with selector=email DKIM record")
    @Test
    void testVerifyDkimPassWithLiveDnsRecord() {
        DkimVerifier verifier = new DkimVerifier(new DnsResolver());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("From", "Sender <sender@" + DOMAIN + ">");
        headers.put("DKIM-Signature",
                "v=1; a=rsa-sha256; d=" + DOMAIN + "; s=" + SELECTOR + "; bh=dGVzdA==; b=dGVzdA==;");

        DkimVerifier.Result result = verifier.verify(headers, new byte[0]);

        assertThat(result.result()).isEqualTo(DkimResult.PASS);
        assertThat(result.domain()).isEqualTo(DOMAIN);
        assertThat(result.selector()).isEqualTo(SELECTOR);
    }

    @DisplayName("gmail.com SPF verification result should be determined without DNS errors")
    @Test
    void testVerifySpfWithoutDnsErrors() throws Exception {
        SpfVerifier verifier = new SpfVerifier(new DnsResolver());

        SpfResult result = verifier.verify(DOMAIN, InetAddress.getByName("203.0.113.10"));

        assertThat(result).isNotIn(SpfResult.TEMPERROR, SpfResult.PERMERROR, SpfResult.NONE);
    }

    @DisplayName("gmail.com DMARC verification result should be PASS without DNS errors")
    @Test
    void testVerifyDmarcPassWithoutDnsErrors() {
        DmarcVerifier verifier = new DmarcVerifier(new DnsResolver());

        DmarcResult result = verifier.evaluate(DOMAIN, true, SpfResult.FAIL, "other.example");

        assertThat(result.evaluation()).isNotIn(DmarcResult.Evaluation.TEMPERROR, DmarcResult.Evaluation.PERMERROR);
        assertThat(result.pass()).isTrue();
    }

    @DisplayName("DKIM lookup with non-existent selector for gmail.com should return NOT_FOUND")
    @Test
    void testDkimLookupWithNonExistentSelector() {
        DnsResolver resolver = new DnsResolver();

        String dkimName = "nonexistent._domainkey." + DOMAIN;
        DnsResolver.QueryResult result = resolver.txtLookup(dkimName);

        assertThat(result.status()).isEqualTo(DnsResolver.QueryStatus.NOT_FOUND);
    }

    @DisplayName("DKIM verification with non-existent selector should return FAIL")
    @Test
    void testDkimVerifyFailsWithNonExistentSelector() {
        DkimVerifier verifier = new DkimVerifier(new DnsResolver());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("From", "Sender <sender@" + DOMAIN + ">");
        headers.put("DKIM-Signature",
                "v=1; a=rsa-sha256; d=" + DOMAIN + "; s=nonexistent; bh=dGVzdA==; b=dGVzdA==;");

        DkimVerifier.Result result = verifier.verify(headers, new byte[0]);

        assertThat(result.result()).isEqualTo(DkimResult.FAIL);
    }

    @DisplayName("DNS TXT lookup with non-existent domain should return NOT_FOUND")
    @Test
    void testDnsLookupWithNonExistentDomain() {
        DnsResolver resolver = new DnsResolver();

        DnsResolver.QueryResult result = resolver.txtLookup("this-domain-does-not-exist-xyz123.com");

        assertThat(result.status()).isEqualTo(DnsResolver.QueryStatus.NOT_FOUND);
    }

    @DisplayName("SPF verification with non-existent domain should return NONE")
    @Test
    void testSpfVerifyNoneForNonExistentDomain() throws Exception {
        SpfVerifier verifier = new SpfVerifier(new DnsResolver());

        SpfResult result = verifier.verify("this-domain-does-not-exist-xyz123.com",
                InetAddress.getByName("203.0.113.10"));

        assertThat(result).isEqualTo(SpfResult.NONE);
    }

    @DisplayName("DMARC verification with non-existent domain should return Evaluation.NONE")
    @Test
    void testDmarcVerifyNoneForNonExistentDomain() {
        DmarcVerifier verifier = new DmarcVerifier(new DnsResolver());

        DmarcResult result = verifier.evaluate("this-domain-does-not-exist-xyz123.com",
                true, SpfResult.FAIL, "other.example");

        assertThat(result.evaluation()).isEqualTo(DmarcResult.Evaluation.NONE);
    }

    @DisplayName("DKIM verification with non-existent domain should return FAIL")
    @Test
    void testDkimVerifyFailsForNonExistentDomain() {
        DkimVerifier verifier = new DkimVerifier(new DnsResolver());
        String fakeDomain = "this-domain-does-not-exist-xyz123.com";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("From", "Sender <sender@" + fakeDomain + ">");
        headers.put("DKIM-Signature",
                "v=1; a=rsa-sha256; d=" + fakeDomain + "; s=email; bh=dGVzdA==; b=dGVzdA==;");

        DkimVerifier.Result result = verifier.verify(headers, new byte[0]);

        assertThat(result.result()).isEqualTo(DkimResult.FAIL);
    }

    private String lowercase(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
