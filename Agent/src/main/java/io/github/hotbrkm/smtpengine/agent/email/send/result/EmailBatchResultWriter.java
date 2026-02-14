package io.github.hotbrkm.smtpengine.agent.email.send.result;

/**
 * Interface for recording email batch results.
 */
public interface EmailBatchResultWriter {

    /**
     * Writes target data.
     *
     * @param progress target processing result to write
     */
    void writeResult(EmailSendProgress progress);

    /**
     * Closes resources.
     */
    void close();
}
