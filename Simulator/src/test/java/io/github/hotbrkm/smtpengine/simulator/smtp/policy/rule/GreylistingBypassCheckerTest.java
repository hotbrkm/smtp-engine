package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.subethamail.smtp.MessageContext;

import java.net.InetSocketAddress;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GreylistingBypassChecker Test")
class GreylistingBypassCheckerTest {

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
    @DisplayName("When no bypass configuration")
    class NullBypassTest {

        @Test
        @DisplayName("Returns false when bypass is null")
        void shouldReturnFalseWhenBypassIsNull() {
            // Given
            GreylistingBypassChecker checker = new GreylistingBypassChecker(null);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isFalse();
        }
    }

    @Nested
    @DisplayName("IP whitelist bypass")
    class IpWhitelistTest {

        @Test
        @DisplayName("Bypasses when IP matches whitelist")
        void shouldBypassWhitelistedIp() {
            // Given
            SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
            bypass.setWhitelistedIps(Arrays.asList("192.168.1.100", "10.0.0.1"));
            GreylistingBypassChecker checker = new GreylistingBypassChecker(bypass);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isTrue();
        }

        @Test
        @DisplayName("Bypasses when IP matches CIDR range")
        void shouldBypassIpInCidrRange() {
            // Given
            SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
            bypass.setWhitelistedIps(Arrays.asList("192.168.1.0/24"));
            GreylistingBypassChecker checker = new GreylistingBypassChecker(bypass);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isTrue();
        }

        @Test
        @DisplayName("Does not bypass IP not in whitelist")
        void shouldNotBypassUnlistedIp() {
            // Given
            SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
            bypass.setWhitelistedIps(Arrays.asList("10.0.0.1"));
            GreylistingBypassChecker checker = new GreylistingBypassChecker(bypass);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isFalse();
        }
    }

    @Nested
    @DisplayName("Sender whitelist bypass")
    class SenderWhitelistTest {

        @Test
        @DisplayName("Bypasses when sender email matches")
        void shouldBypassWhitelistedSender() {
            // Given
            SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
            bypass.setWhitelistedSenders(Arrays.asList("sender@example.com"));
            GreylistingBypassChecker checker = new GreylistingBypassChecker(bypass);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isTrue();
        }

        @Test
        @DisplayName("Bypasses when sender domain matches")
        void shouldBypassWhitelistedSenderDomain() {
            // Given
            SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
            bypass.setWhitelistedSenders(Arrays.asList("@example.com"));
            GreylistingBypassChecker checker = new GreylistingBypassChecker(bypass);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isTrue();
        }

        @Test
        @DisplayName("Does not bypass when sender is not in whitelist")
        void shouldNotBypassUnlistedSender() {
            // Given
            SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
            bypass.setWhitelistedSenders(Arrays.asList("other@other.com"));
            GreylistingBypassChecker checker = new GreylistingBypassChecker(bypass);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isFalse();
        }
    }

    @Nested
    @DisplayName("Recipient whitelist bypass")
    class RecipientWhitelistTest {

        @Test
        @DisplayName("Bypasses when recipient email matches")
        void shouldBypassWhitelistedRecipient() {
            // Given
            SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
            bypass.setWhitelistedRecipients(Arrays.asList("recipient@example.com"));
            GreylistingBypassChecker checker = new GreylistingBypassChecker(bypass);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isTrue();
        }

        @Test
        @DisplayName("Bypasses when recipient domain matches")
        void shouldBypassWhitelistedRecipientDomain() {
            // Given
            SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
            bypass.setWhitelistedRecipients(Arrays.asList("@example.com"));
            GreylistingBypassChecker checker = new GreylistingBypassChecker(bypass);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isTrue();
        }
    }

    @Nested
    @DisplayName("Bypass priority")
    class BypassPriorityTest {

        @Test
        @DisplayName("IP whitelist is checked first")
        void shouldCheckIpFirst() {
            // Given: IP matches, sender/recipient do not match
            SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
            bypass.setWhitelistedIps(Arrays.asList("192.168.1.100"));
            bypass.setWhitelistedSenders(Arrays.asList("other@other.com"));
            bypass.setWhitelistedRecipients(Arrays.asList("other@other.com"));
            GreylistingBypassChecker checker = new GreylistingBypassChecker(bypass);

            // When & Then
            assertThat(checker.shouldBypass(policyContext)).isTrue();
        }
    }
}
