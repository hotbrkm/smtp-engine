package io.github.hotbrkm.smtpengine.agent.email.mime;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AttachmentLoader {

    private final AttachmentReader attachmentReader = new AttachmentReader();

    public Map<String, byte[]> getAttachmentFiles(EmailSendTarget target) throws Exception {
        List<AttachmentMedia> mediaList = target.getAttachments();
        if (mediaList.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, byte[]> attachmentMap = new HashMap<>();
        for (AttachmentMedia media : mediaList) {
            byte[] attachmentBytes = attachmentReader.read(target, media);
            attachmentMap.put(media.fileName(), attachmentBytes);
        }
        return attachmentMap;
    }

}
