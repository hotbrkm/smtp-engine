package io.github.hotbrkm.smtpengine.agent.email.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Getter
public class EmailDomain {
    private final String domainName;
    private final int sessionCount;
    private final int sendCountPerSession;
    private final int connectTimeout;
    private final int readTimeout;
    private final LocalDateTime blockEndTime;

    /**
     * EmailDomain constructor.
     *
     * @param domainName          Domain name
     * @param sessionCount        Session count
     * @param sendCountPerSession Send count per session
     * @param connectTimeout      Connection timeout (in milliseconds)
     * @param readTimeout         Read timeout (in milliseconds)
     * @param blockEndTime        Block end time string (yyyyMMddHHmmss format or empty string)
     */
    public EmailDomain(String domainName, int sessionCount, int sendCountPerSession, int connectTimeout, int readTimeout, String blockEndTime) {
        this.domainName = domainName;
        this.sessionCount = sessionCount;
        this.sendCountPerSession = sendCountPerSession;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.blockEndTime = parseBlockEndTime(blockEndTime);
    }

    /**
     * Converts blockEndTimeString to LocalDateTime.
     *
     * @param blockEndTimeString Block end time string (yyyyMMddHHmmss format or empty string)
     * @return Converted LocalDateTime value or null
     */
    private LocalDateTime parseBlockEndTime(String blockEndTimeString) {
        if (blockEndTimeString == null || blockEndTimeString.isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(blockEndTimeString, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Checks if the given time (LocalDateTime) is before the block end time (blockEndTime).
     *
     * @param dateTime Time to check
     * @return true if before, false otherwise
     */
    public boolean isWithinBlockEndTime(LocalDateTime dateTime) {
        if (blockEndTime == null) {
            return false;
        }
        return dateTime.isBefore(blockEndTime);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EmailDomain that = (EmailDomain) o;
        return Objects.equals(domainName, that.domainName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(domainName);
    }
}
