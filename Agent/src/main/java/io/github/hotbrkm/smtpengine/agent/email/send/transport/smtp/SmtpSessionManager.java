package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.routing.RoutingService;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomain;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.network.SocketConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class SmtpSessionManager {
    private final EmailConfig.Smtp smtpConfig;
    private final EmailConfig.Send sendConfig;
    private SmtpSession session;
    private SmtpClient smtpClient;
    private final EmailDomainManager emailDomainManager;
    private final RoutingService routingService;

    /**
     * SmtpHelper constructor.
     *
     * @param emailConfig         Email configuration
     * @param emailDomainManager  Email domain manager
     * @param routingService      Routing service (policy + DNS)
     */
    public SmtpSessionManager(EmailConfig emailConfig, EmailDomainManager emailDomainManager, RoutingService routingService) {
        smtpConfig = emailConfig.getSmtp();
        sendConfig = emailConfig.getSend();
        this.emailDomainManager = emailDomainManager;
        this.routingService = routingService;
    }

    /**
     * Returns MX/A resolution results for the target domain, reflecting routing policy.
     *
     * @param domain Recipient domain
     * @return List of target IPs to attempt connection
     */
    public List<String> resolveDomainToIpAddresses(String domain) {
        return routingService.resolveTargets(domain);
    }

    /**
     * Creates an SMTP client reflecting domain policy and bind IP.
     *
     * @param domainName Recipient domain
     * @param bindIp     Local bind IP
     * @return SMTP client
     */
    public SmtpClient getSmtpConnector(String domainName, String bindIp) {
        SocketConfig socketConfig = getSocketConfig(getConnectionTimeout(domainName), getReadTimeout(domainName), bindIp);
        SmtpTlsConfig smtpTlsConfig = getSmtpTlsConfig(domainName);
        boolean traceLog = sendConfig.isDnsTrace() || sendConfig.isSmtpTrace();
        return new SmtpClient(socketConfig, smtpTlsConfig, traceLog);
    }

    private SmtpTlsConfig getSmtpTlsConfig(String domain) {
        String[] enabledTlsProtocols = sendConfig.getTlsEnabledProtocols().toArray(new String[0]);
        int maxAttempts = sendConfig.getTlsMaxAttempts();
        long retryDelayMillis = sendConfig.getTlsRetryDelay();
        boolean tlsRequired = sendConfig.isTlsRequired(domain);
        return new SmtpTlsConfig(enabledTlsProtocols, maxAttempts, retryDelayMillis, tlsRequired);
    }

    private SocketConfig getSocketConfig(int connectionTimeout, int readTimeout, String bindIp) {
        return new SocketConfig(bindIp, connectionTimeout, readTimeout);
    }

    private int getReadTimeout(String domain) {
        EmailDomain emailDomain = emailDomainManager.getEmailDomain(domain.toUpperCase());
        return emailDomain.getReadTimeout() * 1000;
    }

    private int getConnectionTimeout(String d) {
        EmailDomain emailDomain = emailDomainManager.getEmailDomain(d.toUpperCase());
        return emailDomain.getConnectTimeout() * 1000;
    }

    /**
     * Creates an SMTP session and stores it internally.
     *
     * @param domain Domain name
     * @param bindIp Local socket bind IP
     */
    public void openSession(String domain, String bindIp) {
        if (session != null) {
            log.warn("Session already exists. Closing existing session before opening new one.");
            closeSession();
        }

        List<String> ipAddresses = resolveDomainToIpAddresses(domain);
        smtpClient = getSmtpConnector(domain, bindIp);
        session = smtpClient.createSession(ipAddresses, smtpConfig.getHelo());
        assertSessionValid(bindIp);
    }

    private void assertSessionValid(String bindIp) {
        if (!isSessionValid()) {
            String message = smtpClient.getCommandHandler().getCurrentMessage();
            throwSessionOpenException(message, bindIp);
        }
    }

    private void throwSessionOpenException(String message, String bindIp) {
        int statusCode = parseStatusCode(message);
        boolean localBindError = isLocalBindError(message);
        closeSession();
        throw new SmtpSessionOpenException(statusCode, message, bindIp, localBindError);
    }

    /**
     * Closes the SMTP session and releases resources.
     */
    public void closeSession() {
        if (session != null) {
            try {
                session.close();
                log.debug("SMTP session closed successfully");
            } catch (Exception e) {
                log.error("Failed to close SMTP session", e);
            }
        }
        session = null;
        smtpClient = null;
    }

    /**
     * Checks if the current session is valid.
     *
     * @return true if session exists and is valid
     */
    public boolean isSessionValid() {
        return smtpClient != null && smtpClient.getCommandHandler().isValidSession();
    }


    /**
     * Sends MAIL FROM command.
     *
     * @param mailFrom Sender email address
     * @return SMTP command response
     */
    public SmtpCommandResponse sendMailFrom(String mailFrom) {
        if (smtpClient == null) {
            throw new IllegalStateException("SMTP session is not open. Call openSession() first.");
        }
        return smtpClient.sendMailFrom(mailFrom);
    }

    /**
     * Sends RCPT TO command.
     *
     * @param rcptTo Recipient email address
     * @return SMTP command response
     */
    public SmtpCommandResponse sendRcptTo(String rcptTo) {
        if (smtpClient == null) {
            throw new IllegalStateException("SMTP session is not open. Call openSession() first.");
        }
        return smtpClient.sendRcptTo(rcptTo);
    }

    /**
     * Sends DATA command.
     *
     * @return SMTP command response
     */
    public SmtpCommandResponse sendData() {
        if (smtpClient == null) {
            throw new IllegalStateException("SMTP session is not open. Call openSession() first.");
        }
        return smtpClient.sendData();
    }

    /**
     * Sends the mail body.
     *
     * @param message Mail body content
     * @param domain  Domain (for timeout configuration)
     * @return SMTP command response
     */
    public SmtpCommandResponse sendMessage(String message, String domain) {
        if (smtpClient == null) {
            throw new IllegalStateException("SMTP session is not open. Call openSession() first.");
        }

        try {
            // Change timeout only when transmitting DATA
            session.setSoTimeout(sendConfig.getDataReadTimeout() * 1000);

            SmtpCommandResponse commandResponse = smtpClient.sendMessage(message);

            // Restore timeout
            EmailDomain emailDomain = emailDomainManager.getEmailDomain(domain.toUpperCase());
            session.setSoTimeout(emailDomain.getReadTimeout() * 1000);

            return commandResponse;
        } catch (Exception e) {
            // Return response even if timeout restoration fails
            log.error("Failed to send message with timeout handling", e);
            throw new RuntimeException("Failed to send message", e);
        }
    }

    /**
     * Sends RSET command.
     *
     * @return SMTP command response
     */
    public SmtpCommandResponse sendRset() {
        if (smtpClient == null) {
            throw new IllegalStateException("SMTP session is not open. Call openSession() first.");
        }
        return smtpClient.sendRset();
    }

    /**
     * Sends QUIT command.
     *
     * @return SMTP command response
     */
    public SmtpCommandResponse sendQuit() {
        if (smtpClient == null) {
            throw new IllegalStateException("SMTP session is not open. Call openSession() first.");
        }
        return smtpClient.sendQuit();
    }

    private int parseStatusCode(String message) {
        if (message == null || message.length() < 3) {
            return SmtpStatus.SESSION_INVALID;
        }
        try {
            return Integer.parseInt(message.substring(0, 3));
        } catch (Exception e) {
            return SmtpStatus.SESSION_INVALID;
        }
    }

    private boolean isLocalBindError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("cannot assign requested address")
                || lower.contains("bindexception")
                || lower.contains("can't assign requested address");
    }
}
