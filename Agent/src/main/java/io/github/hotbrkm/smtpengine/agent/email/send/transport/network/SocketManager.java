package io.github.hotbrkm.smtpengine.agent.email.send.transport.network;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@Slf4j
public class SocketManager {

    @Setter
    private SocketConfig config;
    private Socket socket;

    public SocketManager() {
    }

    public SocketManager(SocketConfig config) {
        this.config = config;
    }

    public Socket createSocket() throws IOException {
        Socket socket = new Socket();
        socket.bind(new InetSocketAddress(config.getBindIp(), 0));
        socket.connect(new InetSocketAddress(config.getServerIp(), config.getPort()), config.getConnectionTimeout());
        socket.setSoTimeout(config.getReadTimeout());
        this.socket = socket;
        return socket;
    }

    public Socket recreateSocket() throws IOException {
        if (this.socket != null) {
            closeQuietly(socket);
        }

        return createSocket();
    }

    public SSLSocket upgradeToSslSocket(String[] enabledTlsProtocols, int maxAttempts, long retryDelayMillis) throws IOException {
        SslSocketConverter sslSocketConverter = new SslSocketConverter(maxAttempts, retryDelayMillis);
        return sslSocketConverter.upgradeToSslSocket(socket, enabledTlsProtocols);
    }

    public String getServerIp() {
        return config.getServerIp();
    }

    public void setIpAddress(IpAddress ipAddress) {
        this.config.setServerIp(ipAddress.getIp());
        this.config.setPort(ipAddress.getPort());
    }

    public void close() {
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                log.debug("Failed to close socket", e);
            }
        }
    }
}
