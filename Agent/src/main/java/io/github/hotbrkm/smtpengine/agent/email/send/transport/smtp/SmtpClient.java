package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import io.github.hotbrkm.smtpengine.agent.email.send.transport.network.SocketConfig;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.network.SocketManager;
import io.github.hotbrkm.smtpengine.agent.email.send.transport.network.IpAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.util.Collections;
import java.util.List;

import static io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommand.CONNECT;
import static io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommand.STARTTLS;

@Slf4j
public class SmtpClient {

    private static final int SMTP_PORT = 25;

    private final SocketManager socketManager;
    private final SmtpTlsConfig smtpTlsConfig;
    private final SmtpCommandHandler smtpCommandHandler;

    @Getter
    private final SmtpSession sessionInfo;

    @Getter
    private String helo;

    public SmtpClient(SocketConfig socketConfig, SmtpTlsConfig smtpTlsConfig) {
        this(socketConfig, smtpTlsConfig, false);
    }

    public SmtpClient(SocketConfig socketConfig, SmtpTlsConfig smtpTlsConfig, boolean traceLog) {
        this.sessionInfo = new SmtpSession();
        this.socketManager = new SocketManager(socketConfig);
        this.smtpTlsConfig = smtpTlsConfig;
        this.smtpCommandHandler = new SmtpCommandHandler(sessionInfo);
        this.smtpCommandHandler.setTraceLog(traceLog);
    }

    public SmtpSession createSession(List<String> ipAddresses, String helo) {
        this.helo = helo;
        SmtpCommandResponse connectResponse = connect(ipAddresses);

        if (!connectResponse.isSuccess()) {
            return sessionInfo;
        }

        SmtpCommandResponse heloResponse = sendEhloOrHelo(helo);

        if (smtpTlsConfig.tlsRequired() && heloResponse.contains(STARTTLS.name())) {
            SmtpCommandResponse tlsResponse = smtpCommandHandler.sendStartTls();

            if (tlsResponse.isSuccess()) {
                processStartTls(sessionInfo, smtpCommandHandler);
            } else {
                smtpCommandHandler.sendHelo(getHelo());
            }
        }

        return sessionInfo;
    }

    public SmtpCommandResponse connect(List<String> ipAddresses) {
        if (ipAddresses.isEmpty()) {
            List<String> responseStrings = Collections.singletonList("600 Resolved IP Address is empty");
            SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(CONNECT, responseStrings);
            smtpCommandHandler.addSmtpCommandResponse(smtpCommandResponse);
            return smtpCommandResponse;
        }

        SmtpCommandResponse connectResponse = null;

        for (String ipAddress : ipAddresses) {
            connectResponse = initializeSession(ipAddress);
            smtpCommandHandler.addSmtpCommandResponse(connectResponse);

            if (connectResponse.isSuccess()) {
                return readInitResponse();
            } else {
                sessionInfo.closeSocket();
            }
        }

        return connectResponse;
    }

    private SmtpCommandResponse initializeSession(String ipAddressStr) {
        socketManager.setIpAddress(getIpAddress(ipAddressStr));
        String message = createSocket();
        return new SmtpCommandResponse(CONNECT, Collections.singletonList(message));
    }

    private IpAddress getIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            throw new IllegalArgumentException("IP Address is empty");
        }

        // If ipAddress contains a colon (:), it includes port information.
        if (ipAddress.contains(":")) {
            String[] split = ipAddress.split(":");
            return new IpAddress(split[0], Integer.parseInt(split[1]));
        } else {
            return new IpAddress(ipAddress, SMTP_PORT);
        }
    }

    private String createSocket() {
        String ip = socketManager.getServerIp();
        try {
            Socket socket = socketManager.createSocket();
            sessionInfo.changeSocket(socket);
            return "250 Connection OK";
        } catch (NoRouteToHostException e) {
            return "601 connect to " + ip + " " + e.toString();
        } catch (Exception e) {
            return "602 connect to " + ip + " " + e.toString();
        }
    }

    private SmtpCommandResponse readInitResponse() {
        return smtpCommandHandler.readInitResponse();
    }

    public SmtpCommandResponse sendEhloOrHelo(String helo) {
        return smtpCommandHandler.sendEhloOrHelo(helo);
    }

    public SmtpCommandResponse sendEhlo(String helo) {
        return smtpCommandHandler.sendEhlo(helo);
    }

    public SmtpCommandResponse sendHelo(String helo) {
        return smtpCommandHandler.sendHelo(helo);
    }

    /**
     * Executes the STARTTLS command.
     * Upgrades a plain socket to an SSL socket and, if the SSL handshake with the server succeeds,
     * sends the EHLO command. If an exception occurs during this process, reconnects to the server
     * using a plain socket.
     *
     * @param sessionInfo session information
     */
    private void processStartTls(SmtpSession sessionInfo, SmtpCommandHandler smtpCommandHandler) {
        try {
            String[] enabledTlsProtocols = smtpTlsConfig.enabledTlsProtocols();
            int maxAttempts = smtpTlsConfig.maxAttempts();
            long retryDelayMillis = smtpTlsConfig.retryDelayMillis();
            SSLSocket sslSocket = socketManager.upgradeToSslSocket(enabledTlsProtocols, maxAttempts, retryDelayMillis);
            sessionInfo.setSslSocket(sslSocket);
            smtpCommandHandler.sendEhlo(getHelo());
        } catch (IOException e) {
            log.error("SSL handshake failed. Continuing with PlainSocket.", e);
            reconnectUsingPlainSocket(sessionInfo, smtpCommandHandler);
        } catch (Exception e) {
            log.error("Unexpected exception occurred during TLS upgrade.", e);
            reconnectUsingPlainSocket(sessionInfo, smtpCommandHandler);
        }
    }

    /**
     * Recreates a plain socket based on socket configuration and reconnects to the server.
     * If the server connection succeeds, sends the HELO command.
     *
     * @param sessionInfo        session information
     * @param smtpCommandHandler SMTP command handler
     */
    private void reconnectUsingPlainSocket(SmtpSession sessionInfo, SmtpCommandHandler smtpCommandHandler) {
        try {
            Socket newSocket = socketManager.recreateSocket();
            sessionInfo.changeSocket(newSocket);
            SmtpCommandResponse initResponse = readInitResponse();

            if (initResponse.isSuccess()) {
                smtpCommandHandler.sendHelo(getHelo());
            }
        } catch (Exception e) {
            log.error("Failed to reconnect to the server using a plain socket.", e);
            smtpCommandHandler.addResponse(CONNECT, "602 connect to " + socketManager.getServerIp() + " " + e.toString());
        }
    }

    /**
     * Returns the SMTP command handler.
     */
    public SmtpCommandHandler getCommandHandler() {
        return smtpCommandHandler;
    }

    /**
     * Sends the MAIL FROM command.
     *
     * @param mailFrom sender email address
     * @return SMTP command response
     */
    public SmtpCommandResponse sendMailFrom(String mailFrom) {
        return smtpCommandHandler.sendMailFrom(mailFrom);
    }

    /**
     * Sends the RCPT TO command.
     *
     * @param rcptTo recipient email address
     * @return SMTP command response
     */
    public SmtpCommandResponse sendRcptTo(String rcptTo) {
        return smtpCommandHandler.sendRcptTo(rcptTo);
    }

    /**
     * Sends the DATA command.
     *
     * @return SMTP command response
     */
    public SmtpCommandResponse sendData() {
        return smtpCommandHandler.sendData();
    }

    /**
     * Sends the email body.
     *
     * @param message email body content
     * @return SMTP command response
     */
    public SmtpCommandResponse sendMessage(String message) {
        return smtpCommandHandler.sendMessage(message);
    }

    /**
     * Sends the RSET command.
     *
     * @return SMTP command response
     */
    public SmtpCommandResponse sendRset() {
        return smtpCommandHandler.sendRset();
    }

    /**
     * Sends the QUIT command.
     *
     * @return SMTP command response
     */
    public SmtpCommandResponse sendQuit() {
        return smtpCommandHandler.sendQuit();
    }
}
