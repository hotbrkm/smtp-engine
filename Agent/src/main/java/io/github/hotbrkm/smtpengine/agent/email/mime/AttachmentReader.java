package io.github.hotbrkm.smtpengine.agent.email.mime;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;

import java.nio.file.Files;
import java.nio.file.Path;

class AttachmentReader {

    /**
     * Creates email attachment content from file path.
     *
     * @param target Recipient data
     * @param media  Attachment information
     * @return Content as byte array
     * @throws Exception
     */
    public byte[] read(EmailSendTarget target, AttachmentMedia media) throws Exception {
        String filePath = media.filePath();
        return Files.readAllBytes(Path.of(filePath));
    }

}
