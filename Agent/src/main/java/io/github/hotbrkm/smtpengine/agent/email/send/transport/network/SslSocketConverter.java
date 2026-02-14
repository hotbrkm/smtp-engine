package io.github.hotbrkm.smtpengine.agent.email.send.transport.network;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class SslSocketConverter {

    private final int maxAttempts;
    private final long retryDelayMillis;

    public SslSocketConverter(int maxAttempts, long retryDelayMillis) {
        this.maxAttempts = maxAttempts;
        this.retryDelayMillis = retryDelayMillis;
    }

    public SSLSocket upgradeToSslSocket(Socket socket, String[] enabledTlsProtocols) throws IOException {
        int attemptCount = 0;
        SSLSocket sslSocket = null;

        while (attemptCount < maxAttempts) {
            try {
                sslSocket = createSslSocket(socket, enabledTlsProtocols);
                sslSocket.startHandshake();
                return sslSocket;
            } catch (SSLException | UnknownHostException e) {
                // SSLException and UnknownHostException are not retryable, so throw immediately
                throw new RuntimeException("exception occurred, not retryable.", e);
            } catch (IOException e) {
                attemptCount++;
                if (attemptCount >= maxAttempts) {
                    throw new IOException("Failed to convert socket to SSLSocket after " + maxAttempts + " attempts", e);
                }
                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException ie) {
                }
            } catch (Exception e) {
                throw new RuntimeException("unexpected exception occurred.", e);
            }
        }

        return sslSocket;
    }

    private SSLSocket createSslSocket(Socket socket, String[] tlsVersions) throws IOException {
        SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, socket.getInetAddress().getHostAddress(),
                socket.getPort(), true);

        if (tlsVersions != null && tlsVersions.length > 0) {
            sslSocket.setEnabledProtocols(tlsVersions);
        }

        return sslSocket;
    }
}
