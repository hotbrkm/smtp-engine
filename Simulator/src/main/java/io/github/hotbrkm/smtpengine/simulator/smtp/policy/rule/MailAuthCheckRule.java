package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.metrics.PolicyMetricsRecorder;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dkim.DkimResult;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dkim.DkimVerifier;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dmarc.DmarcResult;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.dmarc.DmarcVerifier;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf.SpfResult;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.auth.spf.SpfVerifier;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyReason;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import io.github.hotbrkm.smtpengine.simulator.smtp.util.EmailUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class MailAuthCheckRule implements PolicyRule {

    public static final String ATTR_AUTH_RESULTS_HEADER = "mail-auth.authentication-results";

    private final SimulatorSmtpProperties.PolicySet.MailAuthCheck config;
    private final SpfVerifier spfVerifier;
    private final DkimVerifier dkimVerifier;
    private final DmarcVerifier dmarcVerifier;
    private final PolicyMetricsRecorder metricsRecorder;

    public MailAuthCheckRule(SimulatorSmtpProperties.PolicySet.MailAuthCheck config) {
        this(config, new PolicyMetricsRecorder(null));
    }

    public MailAuthCheckRule(SimulatorSmtpProperties.PolicySet.MailAuthCheck config,
                             PolicyMetricsRecorder metricsRecorder) {
        this(config, new SpfVerifier(), new DkimVerifier(), new DmarcVerifier(), metricsRecorder);
    }

    MailAuthCheckRule(SimulatorSmtpProperties.PolicySet.MailAuthCheck config,
                      SpfVerifier spfVerifier,
                      DkimVerifier dkimVerifier,
                      DmarcVerifier dmarcVerifier,
                      PolicyMetricsRecorder metricsRecorder) {
        this.config = Objects.requireNonNull(config);
        this.spfVerifier = Objects.requireNonNull(spfVerifier);
        this.dkimVerifier = Objects.requireNonNull(dkimVerifier);
        this.dmarcVerifier = Objects.requireNonNull(dmarcVerifier);
        this.metricsRecorder = metricsRecorder == null ? new PolicyMetricsRecorder(null) : metricsRecorder;
    }

    @Override
    public boolean supports(SmtpPhase phase) {
        return phase == SmtpPhase.DATA_END;
    }

    @Override
    public PolicyOutcome evaluate(PolicyContext context) {
        if (context == null || context.getPhase() != SmtpPhase.DATA_END || !config.isEnabled()) {
            return PolicyOutcome.allow();
        }

        byte[] raw = context.getRawMessageBytes();
        if (raw == null || raw.length == 0) {
            return PolicyOutcome.allow();
        }

        try {
            Map<String, String> headers = parseHeaders(raw);
            String fromHeader = headers.getOrDefault("From", "");
            String fromDomain = EmailUtil.extractDomain(fromHeader);
            String mailFromDomain = EmailUtil.extractDomain(context.getMailFrom());
            InetAddress clientIp = extractClientIp(context);

            SpfResult spf = SpfResult.NONE;
            if (config.getSpf() != null && config.getSpf().isEnabled()) {
                spf = spfVerifier.verify(mailFromDomain, clientIp);
            }

            DkimResult dkim = DkimResult.NONE;
            boolean dkimAligned = false;
            if (config.getDkim() != null && config.getDkim().isEnabled()) {
                DkimVerifier.Result result = dkimVerifier.verify(headers, raw);
                dkim = result.result();
                dkimAligned = dkim == DkimResult.PASS;
            }

            DmarcResult dmarc = new DmarcResult(true, DmarcResult.Disposition.NONE, fromDomain, DmarcResult.Evaluation.NONE);
            if (config.getDmarc() != null && config.getDmarc().isEnabled()) {
                dmarc = dmarcVerifier.evaluate(fromDomain, dkimAligned, spf, mailFromDomain);
            }

            if (config.isAddAuthenticationResults()) {
                String authResults = buildAuthenticationResults(spf, dkim, dmarc);
                context.putAttribute(ATTR_AUTH_RESULTS_HEADER, authResults);
            }
            recordAuthMetrics(spf, dkim, dmarc);

            log.debug("mail-auth-check result: spf={}, dkim={}, dmarc.pass={}, dmarc.disposition={}, dmarc.evaluation={}",
                    spf, dkim, dmarc.pass(), dmarc.disposition(), dmarc.evaluation());

            if (!isEnforceMode()) {
                return PolicyOutcome.allow();
            }

            PolicyOutcome temp = evaluateTempErrors(spf, dkim, dmarc);
            if (temp != null) {
                return temp;
            }

            PolicyOutcome dkimOutcome = evaluateDkimFailure(dkim);
            if (dkimOutcome != null) {
                return dkimOutcome;
            }

            PolicyOutcome dmarcOutcome = evaluateDmarcFailure(dmarc);
            if (dmarcOutcome != null) {
                return dmarcOutcome;
            }

            PolicyOutcome spfOutcome = evaluateSpfFailure(spf);
            if (spfOutcome != null) {
                return spfOutcome;
            }

            return PolicyOutcome.allow();
        } catch (Exception e) {
            log.warn("mail-auth-check failed unexpectedly", e);
            if (!isEnforceMode()) {
                return PolicyOutcome.allow();
            }
            return PolicyOutcome.tempFail(451,
                    "4.7.1 Temporary authentication check failure",
                    PolicyReason.MAIL_AUTH_CHECK);
        }
    }

    private PolicyOutcome evaluateTempErrors(SpfResult spf, DkimResult dkim, DmarcResult dmarc) {
        if (spf == SpfResult.TEMPERROR && levelActive(config.getSpf().getLevel())) {
            return PolicyOutcome.tempFail(451, "4.7.1 SPF lookup temporary failure", PolicyReason.MAIL_AUTH_CHECK);
        }
        if (dkim == DkimResult.TEMPERROR && levelActive(config.getDkim().getLevel())) {
            return PolicyOutcome.tempFail(451, "4.7.1 DKIM lookup temporary failure", PolicyReason.MAIL_AUTH_CHECK);
        }
        if (dmarc != null && dmarc.evaluation() == DmarcResult.Evaluation.TEMPERROR
                && levelActive(config.getDmarc().getLevel())) {
            return PolicyOutcome.tempFail(451, "4.7.1 DMARC lookup temporary failure", PolicyReason.MAIL_AUTH_CHECK);
        }
        return null;
    }

    private PolicyOutcome evaluateDkimFailure(DkimResult dkim) {
        if (dkim == DkimResult.FAIL || dkim == DkimResult.PERMERROR) {
            return levelBasedOutcome(config.getDkim().getLevel(),
                    451,
                    "4.7.1 DKIM signature verification temporary failure",
                    550,
                    "5.7.20 DKIM signature verification failed");
        }
        return null;
    }

    private PolicyOutcome evaluateDmarcFailure(DmarcResult dmarc) {
        if (dmarc == null || config.getDmarc() == null || !config.getDmarc().isEnabled()) {
            return null;
        }
        if (dmarc.evaluation() == DmarcResult.Evaluation.PERMERROR) {
            return levelBasedOutcome(config.getDmarc().getLevel(),
                    451,
                    "4.7.1 DMARC record check temporary failure",
                    550,
                    "5.7.26 DMARC record check failed");
        }
        if (config.getDmarc().isCheckRecordOnly()) {
            return null;
        }
        if (!dmarc.pass() && dmarc.disposition() == DmarcResult.Disposition.REJECT) {
            return levelBasedOutcome(config.getDmarc().getLevel(),
                    451,
                    "4.7.1 DMARC policy temporary failure",
                    550,
                    "5.7.26 DMARC policy reject");
        }
        return null;
    }

    private PolicyOutcome evaluateSpfFailure(SpfResult spf) {
        if (config.getSpf() == null || !config.getSpf().isEnabled()) {
            return null;
        }
        if (!config.getSpf().isVerifyIpMatch()) {
            return null;
        }
        if (spf == SpfResult.FAIL || spf == SpfResult.PERMERROR) {
            return levelBasedOutcome(config.getSpf().getLevel(),
                    451,
                    "4.7.1 SPF policy temporary failure",
                    550,
                    "5.7.23 SPF validation failed");
        }
        return null;
    }

    private PolicyOutcome levelBasedOutcome(String level,
                                            int tempCode,
                                            String tempMessage,
                                            int permCode,
                                            String permMessage) {
        String normalized = normalizeLevel(level);
        return switch (normalized) {
            case "tempfail" -> PolicyOutcome.tempFail(tempCode, tempMessage, PolicyReason.MAIL_AUTH_CHECK);
            case "reject" -> PolicyOutcome.permFail(permCode, permMessage, PolicyReason.MAIL_AUTH_CHECK);
            default -> null;
        };
    }

    private boolean isEnforceMode() {
        return "enforce".equalsIgnoreCase(config.getMode());
    }

    private static boolean levelActive(String level) {
        String normalized = normalizeLevel(level);
        return !"off".equals(normalized);
    }

    private static String normalizeLevel(String level) {
        return level == null ? "warn" : level.trim().toLowerCase(Locale.ROOT);
    }

    private InetAddress extractClientIp(PolicyContext context) {
        if (context.getMessageContext() == null) {
            return null;
        }
        if (!(context.getMessageContext().getRemoteAddress() instanceof InetSocketAddress remote)) {
            return null;
        }
        return remote.getAddress();
    }

    private String buildAuthenticationResults(SpfResult spf, DkimResult dkim, DmarcResult dmarc) {
        StringBuilder builder = new StringBuilder();
        builder.append("spf=").append(spf.name().toLowerCase(Locale.ROOT));
        builder.append("; dkim=").append(dkim.name().toLowerCase(Locale.ROOT));
        builder.append("; dmarc=").append(dmarc.pass() ? "pass" : "fail");
        return builder.toString();
    }

    private void recordAuthMetrics(SpfResult spf, DkimResult dkim, DmarcResult dmarc) {
        metricsRecorder.recordAuthResult("spf", spf == null ? "none" : spf.name().toLowerCase(Locale.ROOT));
        metricsRecorder.recordAuthResult("dkim", dkim == null ? "none" : dkim.name().toLowerCase(Locale.ROOT));
        String dmarcResult = (dmarc == null || dmarc.evaluation() == null)
                ? "none"
                : dmarc.evaluation().name().toLowerCase(Locale.ROOT);
        metricsRecorder.recordAuthResult("dmarc", dmarcResult);
    }

    private Map<String, String> parseHeaders(byte[] raw) {
        Map<String, String> headers = new HashMap<>();
        String text = new String(raw, StandardCharsets.ISO_8859_1);
        int separator = indexOfHeaderBodySeparator(text);
        String headerPart = separator >= 0 ? text.substring(0, separator) : text;

        String[] lines = headerPart.split("\\r?\\n");
        String currentName = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            if ((line.startsWith(" ") || line.startsWith("\t")) && currentName != null) {
                currentValue.append(' ').append(line.trim());
                continue;
            }
            if (currentName != null) {
                headers.put(currentName, currentValue.toString());
                currentName = null;
                currentValue.setLength(0);
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                currentName = line.substring(0, colon).trim();
                currentValue.append(line.substring(colon + 1).trim());
            }
        }
        if (currentName != null) {
            headers.put(currentName, currentValue.toString());
        }
        return headers;
    }

    private int indexOfHeaderBodySeparator(String text) {
        int index = text.indexOf("\r\n\r\n");
        if (index >= 0) {
            return index;
        }
        return text.indexOf("\n\n");
    }
}
