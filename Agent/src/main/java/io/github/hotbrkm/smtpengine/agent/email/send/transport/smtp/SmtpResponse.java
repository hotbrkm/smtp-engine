package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
class SmtpResponse {

    private final List<String> extendedMessages = new ArrayList<>();
    private int statusCode;
    private String message;
    private String originalMessage;

    public void addExtendedMessage(String extendedMessage) {
        this.extendedMessages.add(extendedMessage);
    }

    public boolean contains(String message) {
        return extendedMessages.stream()
                .anyMatch(it -> it.equals(message));
    }

    @Override
    public String toString() {
        StringBuilder responseBuilder = new StringBuilder();

        for (String extMessage : extendedMessages) {
            responseBuilder.append(statusCode).append("-").append(extMessage).append("\n");
        }

        responseBuilder.append(statusCode).append(" ").append(message);
        return responseBuilder.toString();
    }
}
