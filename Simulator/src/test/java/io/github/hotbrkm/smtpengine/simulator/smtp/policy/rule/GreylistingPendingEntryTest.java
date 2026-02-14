package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GreylistingPendingEntry Test")
class GreylistingPendingEntryTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("Creates entry with given values")
        void shouldCreateWithGivenValues() {
            // Given
            Instant now = Instant.now();
            int attemptCount = 3;

            // When
            GreylistingPendingEntry entry = new GreylistingPendingEntry(now, attemptCount);

            // Then
            assertThat(entry.getFirstSeen()).isEqualTo(now);
            assertThat(entry.getAttemptCount()).isEqualTo(attemptCount);
        }
    }

    @Nested
    @DisplayName("Attempt count management")
    class AttemptCountTest {

        @Test
        @DisplayName("Increments attempt count")
        void shouldIncrementAttemptCount() {
            // Given
            GreylistingPendingEntry entry = new GreylistingPendingEntry(Instant.now(), 1);

            // When
            entry.incrementAttemptCount();
            entry.incrementAttemptCount();

            // Then
            assertThat(entry.getAttemptCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Sets attempt count directly")
        void shouldSetAttemptCount() {
            // Given
            GreylistingPendingEntry entry = new GreylistingPendingEntry(Instant.now(), 1);

            // When
            entry.setAttemptCount(10);

            // Then
            assertThat(entry.getAttemptCount()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("firstSeen management")
    class FirstSeenTest {

        @Test
        @DisplayName("Resets firstSeen")
        void shouldResetFirstSeen() {
            // Given
            Instant original = Instant.parse("2024-01-01T00:00:00Z");
            Instant newTime = Instant.parse("2024-01-02T00:00:00Z");
            GreylistingPendingEntry entry = new GreylistingPendingEntry(original, 1);

            // When
            entry.resetFirstSeen(newTime);

            // Then
            assertThat(entry.getFirstSeen()).isEqualTo(newTime);
        }
    }
}
