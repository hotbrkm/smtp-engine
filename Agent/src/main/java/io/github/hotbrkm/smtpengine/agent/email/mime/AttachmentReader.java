package io.github.hotbrkm.smtpengine.agent.email.mime;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;

import java.nio.file.Files;
import java.nio.file.Path;

class AttachmentReader {

    /**
     * <pre>
     * Creates email attachment content.
     *   - UPLOAD: Reads the file uploaded by the user from their PC.
     *   - PATH: Reads the file from the server path configured by the user.
     * </pre>
     *
     * @param target Recipient data
     * @param media  Attachment information
     * @return Content as byte array
     * @throws Exception
     */
    public byte[] read(EmailSendTarget target, AttachmentMedia media) throws Exception {
        AttachmentMediaType mediaType = media.fileType();
        if (AttachmentMediaType.UPLOAD == mediaType || AttachmentMediaType.PATH == mediaType) {
            String filePath = media.filePath();
            return Files.readAllBytes(Path.of(filePath));
        } else {
            throw new IllegalArgumentException("Unsupported file type (" + mediaType.getCode() + ").");
        }
    }

}
