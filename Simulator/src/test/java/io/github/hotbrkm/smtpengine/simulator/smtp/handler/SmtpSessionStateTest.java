package io.github.hotbrkm.smtpengine.simulator.smtp.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SmtpSessionState Test")
class SmtpSessionStateTest {

    private SmtpSessionState sessionState;

    @BeforeEach
    void setUp() {
        sessionState = new SmtpSessionState();
    }

    @Test
    @DisplayName("Can set and retrieve sender address")
    void setAndGetFrom() {
        // given
        String from = "sender@example.com";

        // when
        sessionState.setFrom(from);

        // then
        assertThat(sessionState.getFrom()).isEqualTo(from);
    }

    @Test
    @DisplayName("Can add recipients and retrieve list")
    void addAndGetRecipients() {
        // given
        String recipient1 = "user1@example.com";
        String recipient2 = "user2@example.com";

        // when
        sessionState.addRecipient(recipient1);
        sessionState.addRecipient(recipient2);

        // then
        assertThat(sessionState.getAcceptedRecipients())
                .hasSize(2);
        assertThat(sessionState.getAcceptedRecipients())
                .containsExactly(recipient1, recipient2);
    }

    @Test
    @DisplayName("Can retrieve recipient count")
    void getAcceptedRecipientCount() {
        // given
        sessionState.addRecipient("user1@example.com");
        sessionState.addRecipient("user2@example.com");
        sessionState.addRecipient("user3@example.com");

        // when & then
        assertThat(sessionState.getAcceptedRecipientCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Returns false for hasAcceptedRecipients when no recipients")
    void hasAcceptedRecipients_empty_returnsFalse() {
        // when & then
        assertThat(sessionState.hasAcceptedRecipients()).isFalse();
    }

    @Test
    @DisplayName("Returns true for hasAcceptedRecipients when recipients exist")
    void hasAcceptedRecipients_withRecipients_returnsTrue() {
        // given
        sessionState.addRecipient("user@example.com");

        // when & then
        assertThat(sessionState.hasAcceptedRecipients()).isTrue();
    }

    @Test
    @DisplayName("Can set and retrieve shared RCPT decision")
    void setAndGetSharedRcptDecision() {
        // given
        Integer decision = 550;

        // when
        sessionState.setSharedRcptDecision(decision);

        // then
        assertThat(sessionState.getSharedRcptDecision()).isEqualTo(decision);
        assertThat(sessionState.hasSharedRcptDecision()).isTrue();
    }

    @Test
    @DisplayName("Returns false for hasSharedRcptDecision when not set")
    void hasSharedRcptDecision_notSet_returnsFalse() {
        // when & then
        assertThat(sessionState.hasSharedRcptDecision()).isFalse();
        assertThat(sessionState.getSharedRcptDecision()).isNull();
    }

    @Test
    @DisplayName("Recipient list is immutable")
    void getAcceptedRecipients_returnsImmutableList() {
        // given
        sessionState.addRecipient("user@example.com");

        // when & then
        assertThat(sessionState.getAcceptedRecipients())
                .isUnmodifiable();
    }
}
