package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

public record SmtpTlsConfig(String[] enabledTlsProtocols, int maxAttempts, long retryDelayMillis, boolean tlsRequired) {
}
