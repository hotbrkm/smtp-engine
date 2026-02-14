package io.github.hotbrkm.smtpengine.simulator.smtp.policy.model;

import java.util.Locale;

/**
 * Enumeration of SMTP protocol phases.
 * <p>
 * These phases represent the different stages in an SMTP session where
 * policy rules can be evaluated.
 * </p>
 *
 * @author hotbrkm
 * @since 1.0.0
 */
public enum SmtpPhase {
    /** Before connection is fully established. */
    CONNECT_PRE,
    /** After RCPT TO command, before message data. */
    RCPT_PRE,
    /** Before message data is accepted. */
    DATA_PRE,
    /** After message data (DATA command) is complete. */
    DATA_END,
    /** When the SMTP session ends. */
    SESSION_END;

    /**
     * Converts a configuration value to an SmtpPhase.
     * @param value the configuration value (e.g., "rcpt-pre" or "RCPT_PRE")
     * @return the corresponding SmtpPhase, or null if invalid
     */
    public static SmtpPhase fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return SmtpPhase.valueOf(normalized);
    }
}
