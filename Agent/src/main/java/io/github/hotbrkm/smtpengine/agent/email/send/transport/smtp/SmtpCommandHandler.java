package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp.SmtpCommand.*;

/**
 * Handler that transmits SMTP commands and manages responses.
 * Manages SMTP protocol-level logic and response history.
 */
@Slf4j
public class SmtpCommandHandler {
    private final SmtpSession session;
    private final List<SmtpCommandResponse> responses = new ArrayList<>();

    @Setter
    @Getter
    private boolean traceLog = false;

    public SmtpCommandHandler(SmtpSession session) {
        this.session = session;
    }

    public SmtpCommandResponse readInitResponse() {
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(INIT, sendCommand(null));
        addSmtpCommandResponse(smtpCommandResponse);
        return smtpCommandResponse;
    }

    public List<String> sendCommand(String command) {
        List<String> responseLines = new ArrayList<>();

        try {
            writeMessage(command);
            readAllMessages(responseLines);

            return responseLines;
        } catch (InterruptedIOException e) {
            responseLines.add("704 SMTP " + e);
            return responseLines;
        } catch (IOException e) {
            responseLines.add("703 SMTP " + e);
            return responseLines;
        } catch (Exception e) {
            responseLines.add("700 SMTP " + e);
            return responseLines;
        }
    }

    private void writeMessage(String command) {
        if (command != null) {
            if (traceLog) {
                log.info("[Send Message]: {}", command);
            }
            session.writeMessage(command);
        }
    }

    private void readAllMessages(List<String> responseLines) throws Exception {
        String line;
        do {
            line = session.readLine();
            if (traceLog) {
                log.info("[Read Message]: {}", line);
            }

            if (line == null) {
                throw new Exception("null reply from server");
            }

            responseLines.add(line);
        } while ((line.length() > 3) && (line.charAt(3) == '-'));
    }

    public SmtpCommandResponse sendEhloOrHelo(String command) {
        SmtpCommandResponse smtpCommandResponse = sendEhlo(command);

        if (smtpCommandResponse.isSuccess()) {
            return smtpCommandResponse;
        } else {
            return sendHelo(command);
        }
    }

    public SmtpCommandResponse sendEhlo(String command) {
        List<String> responseStrings = sendCommand(EHLO.buildMessage(command));
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(HELO, responseStrings);
        addSmtpCommandResponse(smtpCommandResponse);
        return smtpCommandResponse;
    }

    public SmtpCommandResponse sendHelo(String command) {
        List<String> responseStrings = sendCommand(HELO.buildMessage(command));
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(HELO, responseStrings);
        addSmtpCommandResponse(smtpCommandResponse);
        return smtpCommandResponse;
    }



    public SmtpCommandResponse sendStartTls() {
        List<String> responseStrings = sendCommand(STARTTLS.name());
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(STARTTLS, responseStrings);
        addSmtpCommandResponse(smtpCommandResponse);
        return smtpCommandResponse;
    }

    public SmtpCommandResponse sendMailFrom(String mailFrom) {
        List<String> responseStrings = sendCommand(MAIL_FROM.buildMessage("<" + mailFrom + ">"));
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(MAIL_FROM, responseStrings);
        addSmtpCommandResponse(smtpCommandResponse);
        return smtpCommandResponse;
    }

    public SmtpCommandResponse sendRcptTo(String rcptTo) {
        List<String> responseStrings = sendCommand(RCPT_TO.buildMessage("<" + rcptTo + ">"));
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(RCPT_TO, responseStrings);
        addSmtpCommandResponse(smtpCommandResponse);
        return smtpCommandResponse;
    }

    public SmtpCommandResponse sendData() {
        List<String> responseStrings = sendCommand(DATA.getCommand());
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(DATA, responseStrings);
        addSmtpCommandResponse(smtpCommandResponse);
        return smtpCommandResponse;
    }

    /**
     * Sends the mail body.
     * Must be called after receiving 354 response to DATA command.
     * Expects 250 response after message transmission.
     */
    public SmtpCommandResponse sendMessage(String message) {
        List<String> responseLines = new ArrayList<>();

        try {
            writeMessage(message + "\r\n.");
            readAllMessages(responseLines);

            // Process response after message transmission as DATA_END (expect 250)
            SmtpCommandResponse response = new SmtpCommandResponse(DATA_END, responseLines);
            addSmtpCommandResponse(response);
            return response;
        } catch (InterruptedIOException e) {
            responseLines.add("704 SMTP DATA " + e);
            return new SmtpCommandResponse(DATA_END, responseLines);
        } catch (IOException e) {
            responseLines.add("700 SMTP DATA " + e);
            return new SmtpCommandResponse(DATA_END, responseLines);
        } catch (Exception e) {
            responseLines.add("700 SMTP DATA " + e);
            return new SmtpCommandResponse(DATA_END, responseLines);
        }
    }

    public SmtpCommandResponse sendRset() {
        List<String> responseStrings = sendCommand(RSET.getCommand());
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(RSET, responseStrings);
        addSmtpCommandResponse(smtpCommandResponse);
        return smtpCommandResponse;
    }

    public SmtpCommandResponse sendQuit() {
        List<String> responseStrings = sendCommand(QUIT.getCommand());
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(QUIT, responseStrings);
        addSmtpCommandResponse(smtpCommandResponse);
        return smtpCommandResponse;
    }

    // ========== Response history management methods ==========

    /**
     * Adds an SMTP command response to the history.
     */
    public void addSmtpCommandResponse(SmtpCommandResponse response) {
        this.responses.add(response);
    }

    /**
     * Creates a response from SMTP command and message, then adds to history.
     */
    public void addResponse(SmtpCommand smtpCommand, String message) {
        SmtpCommandResponse smtpCommandResponse = new SmtpCommandResponse(smtpCommand, Collections.singletonList(message));
        this.responses.add(smtpCommandResponse);
    }

    /**
     * Returns the current (most recent) response message.
     */
    public String getCurrentMessage() {
        if (responses.isEmpty()) {
            return "";
        }

        Optional<SmtpCommandResponse> maybeResponse = getCurrentResponse();
        if (maybeResponse.isPresent()) {
            SmtpCommandResponse smtpCommandResponse = maybeResponse.get();
            return smtpCommandResponse.getOriginalMessage();
        }

        return "";
    }

    /**
     * Returns the current (most recent) response.
     */
    private Optional<SmtpCommandResponse> getCurrentResponse() {
        if (responses.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(responses.get(responses.size() - 1));
    }

    /**
     * Checks if the SMTP session state is normal.
     * The session is considered valid if HELO/EHLO command succeeded in the response history,
     * and no connection termination command (QUIT) has been sent after that.
     *
     * @return true if session state is normal
     */
    public boolean isValidSession() {
        if (responses.isEmpty()) {
            return false;
        }

        // Check if HELO/EHLO command succeeded
        boolean heloSucceeded = false;
        for (SmtpCommandResponse response : responses) {
            if (response.getCommand().equals(HELO) && response.isSuccess()) {
                heloSucceeded = true;
            }
            // Session ends when QUIT command is sent
            if (response.getCommand().equals(QUIT)) {
                return false;
            }
        }

        return heloSucceeded;
    }
}
