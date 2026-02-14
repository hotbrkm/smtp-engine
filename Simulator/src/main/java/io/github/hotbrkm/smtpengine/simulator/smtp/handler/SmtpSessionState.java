package io.github.hotbrkm.smtpengine.simulator.smtp.handler;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the SMTP session state.
 * <p>
 * Encapsulates and manages the session-specific state of the message handler.
 */
public class SmtpSessionState {

    private final List<String> acceptedRecipients = new ArrayList<>();

    @Getter
    @Setter
    private Integer sharedRcptDecision;

    @Getter
    @Setter
    private String from;

    /**
     * Adds a recipient to the list of accepted recipients.
     *
     * @param recipient Recipient email address
     */
    public void addRecipient(String recipient) {
        acceptedRecipients.add(recipient);
    }

    /**
     * Returns the accepted recipient list (immutable).
     *
     * @return Recipient list
     */
    public List<String> getAcceptedRecipients() {
        return Collections.unmodifiableList(acceptedRecipients);
    }

    /**
     * Returns the number of accepted recipients.
     *
     * @return Number of recipients
     */
    public int getAcceptedRecipientCount() {
        return acceptedRecipients.size();
    }

    /**
     * Checks if there are any accepted recipients.
     *
     * @return true if there are recipients
     */
    public boolean hasAcceptedRecipients() {
        return !acceptedRecipients.isEmpty();
    }

    /**
     * Checks if a shared RCPT decision is set.
     *
     * @return true if set
     */
    public boolean hasSharedRcptDecision() {
        return sharedRcptDecision != null;
    }
}
