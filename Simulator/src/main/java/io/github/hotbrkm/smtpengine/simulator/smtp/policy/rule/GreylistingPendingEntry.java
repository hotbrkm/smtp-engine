package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import java.time.Instant;

/**
 * Greylisting pending entry.
 * <p>
 * Tracks the first attempt time and attempt count.
 */
public class GreylistingPendingEntry {

    private Instant firstSeen;
    private int attemptCount;

    /**
     * Creates a new pending entry.
     *
     * @param firstSeen    First attempt time
     * @param attemptCount Attempt count
     */
    public GreylistingPendingEntry(Instant firstSeen, int attemptCount) {
        this.firstSeen = firstSeen;
        this.attemptCount = attemptCount;
    }

    /**
     * Returns the first attempt time.
     */
    public Instant getFirstSeen() {
        return firstSeen;
    }

    /**
     * Resets the first attempt time.
     */
    public void resetFirstSeen(Instant firstSeen) {
        this.firstSeen = firstSeen;
    }

    /**
     * Returns the attempt count.
     */
    public int getAttemptCount() {
        return attemptCount;
    }

    /**
     * Sets the attempt count.
     */
    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    /**
     * Increments the attempt count.
     */
    public void incrementAttemptCount() {
        this.attemptCount++;
    }
}
