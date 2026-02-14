package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EngineRuntimeState batch stats retention period test")
class EngineRuntimeStateBatchStatsRetentionTest {

    @Test
    @DisplayName("Completed/failed counters are retained only for the recent 7 days")
    void batchStats_areRetainedForRecentSevenDaysOnly() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        EngineRuntimeState runtimeState = new EngineRuntimeState(createOptions(), clock);

        LocalDate startDate = LocalDate.of(2026, 1, 1);
        for (int dayOffset = 0; dayOffset < 8; dayOffset++) {
            clock.setInstant(startDate.plusDays(dayOffset).atStartOfDay(ZoneOffset.UTC).toInstant());
            runtimeState.incrementCompletedBatches();
            runtimeState.incrementFailedBatches();
        }

        assertThat(runtimeState.completedBatches()).isEqualTo(7);
        assertThat(runtimeState.failedBatches()).isEqualTo(7);

        clock.setInstant(startDate.plusDays(40).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(runtimeState.completedBatches()).isZero();
        assertThat(runtimeState.failedBatches()).isZero();
    }

    private EngineRuntimeOptions createOptions() {
        EmailConfig.Send send = new EmailConfig.Send();
        send.setBindAddresses(List.of("127.0.0.1"));
        return EngineRuntimeOptions.fromExplicit(send, 1, 100, 1, 100L,
                1_000L, EmailConfig.Send.DEFAULT_BATCH_RETRY_BACKOFF_MULTIPLIER);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zoneId;

        private MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
