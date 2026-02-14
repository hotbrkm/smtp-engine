package io.github.hotbrkm.smtpengine.simulator.smtp.exception;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DisconnectException unit test
 */
@Slf4j
@DisplayName("DisconnectException Test")
class DisconnectExceptionTest {

    @Test
    @DisplayName("Creates DisconnectException and verifies fields")
    void testDisconnectExceptionCreation() {
        // Given
        int code = 421;
        String message = "4.7.0 Session closed. [reason=BLOCKED_DOMAIN,domain=spam.com]";
        boolean shouldClose = true;

        // When
        DisconnectException exception = new DisconnectException(code, message, shouldClose);

        // Then
        assertThat(exception.getCode()).isEqualTo(code);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.shouldCloseConnection()).isTrue();
    }

    @Test
    @DisplayName("DisconnectException is a subclass of RejectException")
    void testDisconnectExceptionInheritance() {
        // Given
        DisconnectException exception = new DisconnectException(421, "Test", true);

        // When & Then
        assertThat(exception).isInstanceOf(org.subethamail.smtp.RejectException.class);
    }
}
