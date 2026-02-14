package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Class that manages SMTP session network connections.
 * Handles socket connections and network I/O.
 */
@Getter
@Setter
@Slf4j
public class SmtpSession implements AutoCloseable {

    private Socket socket;
    private SSLSocket sslSocket;
    private BufferedReader reader;
    private PrintWriter writer;

    public void setSoTimeout(int timeout) {
        try {
            socket.setSoTimeout(timeout);
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    public void writeMessage(String message) {
        if (writer == null) {
            return;
        }

        writer.print(message);
        writer.print("\r\n");
        writer.flush();
    }

    public String readLine() throws IOException {
        if (reader == null) {
            return null;
        }

        return reader.readLine();
    }

    public void changeStream(Socket socket) throws IOException {
        setWriter(new PrintWriter(socket.getOutputStream(), false));
        setReader(new BufferedReader(new InputStreamReader(socket.getInputStream())));
    }

    public void changeSocket(Socket socket) throws IOException {
        close();
        setSocket(socket);
        changeStream(socket);
    }

    public void setSslSocket(SSLSocket sslSocket) throws IOException {
        this.sslSocket = sslSocket;
        changeStream(sslSocket);
    }

    public void closeSocket() {
        close();
        // Kept for legacy logic that checks socket variable for null reference
        this.socket = null;
    }

    @Override
    public void close() {
        closeQuietly(writer);
        closeQuietly(reader);
        closeQuietly(socket);
        closeQuietly(sslSocket);
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
