package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dkim.DkimResult;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dkim.DkimVerifier;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dmarc.DmarcResult;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dmarc.DmarcVerifier;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf.SpfResult;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf.SpfVerifier;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MailAuthCheckRuleTest {

    @Test
    void shouldPreferTempErrorOverDeterministicFailuresInEnforceMode() {
        MailAuthCheckRule rule = new MailAuthCheckRule(
                enforceConfig(),
                new FixedSpfVerifier(SpfResult.TEMPERROR),
                new FixedDkimVerifier(new DkimVerifier.Result(DkimResult.FAIL, "example.com", "selector")),
                new FixedDmarcVerifier(new DmarcResult(false, DmarcResult.Disposition.REJECT, "example.com",
                        DmarcResult.Evaluation.FAIL)),
                null
        );

        PolicyOutcome outcome = rule.evaluate(context(sampleMessage(), true));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.TEMP_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(451);
    }

    @Test
    void shouldApplyDkimBeforeDmarcAndSpfWhenNoTempError() {
        MailAuthCheckRule rule = new MailAuthCheckRule(
                enforceConfig(),
                new FixedSpfVerifier(SpfResult.FAIL),
                new FixedDkimVerifier(new DkimVerifier.Result(DkimResult.FAIL, "example.com", "selector")),
                new FixedDmarcVerifier(new DmarcResult(false, DmarcResult.Disposition.REJECT, "example.com",
                        DmarcResult.Evaluation.FAIL)),
                null
        );

        PolicyOutcome outcome = rule.evaluate(context(sampleMessage(), true));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.PERM_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(550);
        assertThat(outcome.message()).contains("5.7.20");
    }

    @Test
    void shouldApplyDmarcBeforeSpfWhenDkimPassed() {
        MailAuthCheckRule rule = new MailAuthCheckRule(
                enforceConfig(),
                new FixedSpfVerifier(SpfResult.FAIL),
                new FixedDkimVerifier(new DkimVerifier.Result(DkimResult.PASS, "example.com", "selector")),
                new FixedDmarcVerifier(new DmarcResult(false, DmarcResult.Disposition.REJECT, "example.com",
                        DmarcResult.Evaluation.FAIL)),
                null
        );

        PolicyOutcome outcome = rule.evaluate(context(sampleMessage(), true));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.PERM_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(550);
        assertThat(outcome.message()).contains("5.7.26");
    }

    @Test
    void shouldApplySpfWhenDkimAndDmarcPassed() {
        MailAuthCheckRule rule = new MailAuthCheckRule(
                enforceConfig(),
                new FixedSpfVerifier(SpfResult.FAIL),
                new FixedDkimVerifier(new DkimVerifier.Result(DkimResult.PASS, "example.com", "selector")),
                new FixedDmarcVerifier(new DmarcResult(true, DmarcResult.Disposition.NONE, "example.com",
                        DmarcResult.Evaluation.PASS)),
                null
        );

        PolicyOutcome outcome = rule.evaluate(context(sampleMessage(), true));

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.PERM_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(550);
        assertThat(outcome.message()).contains("5.7.23");
    }

    @Test
    void shouldKeepLintModeNonBlockingAndAttachAuthenticationResultsWhenEnabled() {
        MailAuthCheckRule rule = new MailAuthCheckRule(
                lintConfig(true),
                new FixedSpfVerifier(SpfResult.PASS),
                new FixedDkimVerifier(new DkimVerifier.Result(DkimResult.PASS, "example.com", "selector")),
                new FixedDmarcVerifier(new DmarcResult(true, DmarcResult.Disposition.NONE, "example.com",
                        DmarcResult.Evaluation.PASS)),
                null
        );
        PolicyContext context = context(sampleMessage(), true);

        PolicyOutcome outcome = rule.evaluate(context);

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(context.getStringAttribute(MailAuthCheckRule.ATTR_AUTH_RESULTS_HEADER))
                .contains("spf=pass")
                .contains("dkim=pass")
                .contains("dmarc=pass");
    }

    @Test
    void shouldNotAttachAuthenticationResultsWhenDisabled() {
        MailAuthCheckRule rule = new MailAuthCheckRule(
                lintConfig(false),
                new FixedSpfVerifier(SpfResult.PASS),
                new FixedDkimVerifier(new DkimVerifier.Result(DkimResult.PASS, "example.com", "selector")),
                new FixedDmarcVerifier(new DmarcResult(true, DmarcResult.Disposition.NONE, "example.com",
                        DmarcResult.Evaluation.PASS)),
                null
        );
        PolicyContext context = context(sampleMessage(), true);

        PolicyOutcome outcome = rule.evaluate(context);

        assertThat(outcome.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(context.getStringAttribute(MailAuthCheckRule.ATTR_AUTH_RESULTS_HEADER)).isNull();
    }

    private static SimulatorSmtpProperties.PolicySet.MailAuthCheck enforceConfig() {
        SimulatorSmtpProperties.PolicySet.MailAuthCheck config = new SimulatorSmtpProperties.PolicySet.MailAuthCheck();
        config.setMode("enforce");
        config.setAddAuthenticationResults(false);

        config.getSpf().setEnabled(true);
        config.getSpf().setLevel("reject");
        config.getSpf().setVerifyIpMatch(true);

        config.getDkim().setEnabled(true);
        config.getDkim().setLevel("reject");

        config.getDmarc().setEnabled(true);
        config.getDmarc().setLevel("reject");
        config.getDmarc().setCheckRecordOnly(false);
        return config;
    }

    private static SimulatorSmtpProperties.PolicySet.MailAuthCheck lintConfig(boolean addAuthResults) {
        SimulatorSmtpProperties.PolicySet.MailAuthCheck config = new SimulatorSmtpProperties.PolicySet.MailAuthCheck();
        config.setMode("lint");
        config.setAddAuthenticationResults(addAuthResults);
        config.getSpf().setVerifyIpMatch(true);
        return config;
    }

    private static PolicyContext context(byte[] message, boolean withMailFrom) {
        return PolicyContext.builder()
                .phase(SmtpPhase.DATA_END)
                .mailFrom(withMailFrom ? "bounce@example.com" : null)
                .rawMessageBytes(message)
                .build();
    }

    private static byte[] sampleMessage() {
        String message = "From: Sender <sender@example.com>\r\n"
                         + "DKIM-Signature: v=1; a=rsa-sha256; d=example.com; s=selector; bh=abc; b=def;\r\n"
                         + "\r\n"
                         + "body";
        return message.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static class FixedSpfVerifier extends SpfVerifier {
        private final SpfResult result;

        private FixedSpfVerifier(SpfResult result) {
            this.result = result;
        }

        @Override
        public SpfResult verify(String domain, InetAddress ip) {
            return result;
        }
    }

    private static class FixedDkimVerifier extends DkimVerifier {
        private final Result result;

        private FixedDkimVerifier(Result result) {
            this.result = result;
        }

        @Override
        public Result verify(Map<String, String> headers, byte[] rawMessage) {
            return result;
        }
    }

    private static class FixedDmarcVerifier extends DmarcVerifier {
        private final DmarcResult result;

        private FixedDmarcVerifier(DmarcResult result) {
            this.result = result;
        }

        @Override
        public DmarcResult evaluate(String fromDomain, boolean dkimPassAligned, SpfResult spfResult, String spfDomain) {
            return result;
        }
    }
}
