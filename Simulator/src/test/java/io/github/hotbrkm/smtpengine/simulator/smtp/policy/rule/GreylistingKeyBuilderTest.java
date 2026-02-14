package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.MessageContext;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GreylistingKeyBuilder Test")
class GreylistingKeyBuilderTest {

    private MessageContext messageContext;
    private PolicyContext policyContext;

    @BeforeEach
    void setUp() {
        messageContext = mock(MessageContext.class);
        policyContext = mock(PolicyContext.class);
        when(policyContext.getMessageContext()).thenReturn(messageContext);
        when(policyContext.getPhase()).thenReturn(SmtpPhase.RCPT_PRE);

        // Default values
        InetSocketAddress remoteAddr = new InetSocketAddress("192.168.1.100", 12345);
        when(messageContext.getRemoteAddress()).thenReturn(remoteAddr);
        when(policyContext.getMailFrom()).thenReturn("sender@example.com");
        when(policyContext.getCurrentRecipient()).thenReturn("recipient@example.com");
    }

    @Nested
    @DisplayName("Key generation by trackBy mode")
    class TrackByModeTest {

        @Test
        @DisplayName("ip mode: uses IP only")
        void shouldBuildKeyWithIpOnly() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("ip");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("mail-from mode: uses sender only")
        void shouldBuildKeyWithMailFromOnly() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("mail-from");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("sender@example.com");
        }

        @Test
        @DisplayName("rcpt-to mode: uses recipient only")
        void shouldBuildKeyWithRcptToOnly() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("rcpt-to");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("recipient@example.com");
        }

        @Test
        @DisplayName("ip-mail-from mode: combines IP and sender")
        void shouldBuildKeyWithIpAndMailFrom() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("ip-mail-from");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("192.168.1.100|sender@example.com");
        }

        @Test
        @DisplayName("ip-rcpt-to mode: combines IP and recipient")
        void shouldBuildKeyWithIpAndRcptTo() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("ip-rcpt-to");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("192.168.1.100|recipient@example.com");
        }

        @Test
        @DisplayName("mail-from-rcpt-to mode: combines sender and recipient")
        void shouldBuildKeyWithMailFromAndRcptTo() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("mail-from-rcpt-to");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("sender@example.com|recipient@example.com");
        }

        @Test
        @DisplayName("ip-mail-from-rcpt-to mode: combines all")
        void shouldBuildKeyWithAllComponents() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("ip-mail-from-rcpt-to");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("192.168.1.100|sender@example.com|recipient@example.com");
        }

        @Test
        @DisplayName("null trackBy: uses default (all components)")
        void shouldUseDefaultModeWhenTrackByIsNull() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder(null);

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("192.168.1.100|sender@example.com|recipient@example.com");
        }

        @Test
        @DisplayName("Unknown trackBy: uses default (all components)")
        void shouldUseDefaultModeForUnknownTrackBy() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("unknown-mode");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("192.168.1.100|sender@example.com|recipient@example.com");
        }

        @Test
        @DisplayName("Ignores case")
        void shouldIgnoreCase() {
            // Given
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("IP-MAIL-FROM");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("192.168.1.100|sender@example.com");
        }
    }

    @Nested
    @DisplayName("Null value handling")
    class NullHandlingTest {

        @Test
        @DisplayName("null mailFrom is treated as empty string")
        void shouldHandleNullMailFrom() {
            // Given
            when(policyContext.getMailFrom()).thenReturn(null);
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("ip-mail-from-rcpt-to");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("192.168.1.100||recipient@example.com");
        }

        @Test
        @DisplayName("null recipient is treated as empty string")
        void shouldHandleNullRecipient() {
            // Given
            when(policyContext.getCurrentRecipient()).thenReturn(null);
            GreylistingKeyBuilder builder = new GreylistingKeyBuilder("ip-mail-from-rcpt-to");

            // When
            String key = builder.buildKey(policyContext);

            // Then
            assertThat(key).isEqualTo("192.168.1.100|sender@example.com|");
        }
    }
}
