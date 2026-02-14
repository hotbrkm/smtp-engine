package io.github.hotbrkm.smtpengine.agent.email.mime;

import java.util.List;
import java.util.Objects;

/**
 * Attachment media item
 */
public record AttachmentMedia(AttachmentMediaType fileType, String fileName, String filePath, boolean encrypted, String secuField, List<String> childAttachmentPaths) {
    public AttachmentMedia {
        Objects.requireNonNull(fileType, "fileType must not be null");
        if (childAttachmentPaths == null) {
            childAttachmentPaths = List.of();
        }
    }

    public AttachmentMedia(AttachmentMediaType fileType, String fileName, String filePath, boolean encrypted, String secuField) {
        this(fileType, fileName, filePath, encrypted, secuField, List.of());
    }
}
