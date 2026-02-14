package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import lombok.Getter;

import java.util.List;

@Getter
public class SmtpCommandResponse {
    private final SmtpCommand command;
    private final SmtpResponse response;

    public SmtpCommandResponse(SmtpCommand command, List<String> response) {
        this.command = command;

        SmtpResponseParser responseParser = new SmtpResponseParser();
        this.response = responseParser.parseResponse(response);
    }

    public boolean isSuccess() {
        return command.getSuccessCode() == response.getStatusCode();
    }

    public String getOriginalMessage() {
        return response.getOriginalMessage();
    }

    public boolean contains(String message) {
        return response.contains(message);
    }

    public int getStatusCode() {
        return response.getStatusCode();
    }

    public String getMessage() {
        return response.getMessage();
    }

    @Override
    public String toString() {
        return "Command: " + command + ", Response: " + response;
    }
}
