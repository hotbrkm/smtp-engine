package io.github.hotbrkm.smtpengine.agent.email.mime;

import io.github.hotbrkm.smtpengine.agent.email.config.EmailConfig;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendContext;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class EmailMimeComposer {

    private static final String SECURE_HTML = "SECURE";
    private static final String SECURE_PDF = "SECURE_PDF";

    private final EmailConfig emailConfig;
    private final EmailConfig.Dkim dkim;
    private final EmailSendContext emailSendContext;
    private final AttachmentLoader attachmentLoader;
    private DkimSigner dkimSigner;

    public EmailMimeComposer(EmailConfig emailConfig, EmailSendContext emailSendContext, String spoolPath) {
        this.emailConfig = emailConfig;
        this.dkim = emailConfig.getDkim();
        this.emailSendContext = emailSendContext;
        this.attachmentLoader = new AttachmentLoader();

        if (dkim.isEnabled()) {
            try {
                dkimSigner = new DkimSigner(dkim);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String makeMime(EmailSendTarget emailSendTarget) throws Exception {
        String subtype = emailSendContext.templateSubtype();

        if (SECURE_HTML.equals(subtype) || SECURE_PDF.equals(subtype)) {
            return makeMime(emailSendTarget, emailSendTarget.getAttributeString(EmailSendTarget.ATTR_COVER_BODY));
        } else {
            return makeMime(emailSendTarget, emailSendTarget.getBody());
        }
    }

    private String makeMime(EmailSendTarget emailSendTarget, String content) throws Exception {
        MimeMessageBuilder messageBuilder = new MimeMessageBuilder();
        messageBuilder.setFrom(emailSendTarget.getSenderName(), emailSendTarget.getSenderEmail());
        messageBuilder.setTo(emailSendTarget.getTargetName(), emailSendTarget.getTargetEmail());
        messageBuilder.setSubject(emailSendTarget.getTitle());
        messageBuilder.addAlterContent("text/html", content);
        messageBuilder.addAttachments(attachmentLoader.getAttachmentFiles(emailSendTarget));
        messageBuilder.makeHeader(getCustomHeader(emailSendTarget));
        messageBuilder.makeBody();

        if (dkim.isEnabled()) {
            Optional<String> signOptional = dkimSigner.sign(messageBuilder.toString(), messageBuilder.getRcptDomain());
            signOptional.ifPresent(messageBuilder::setExtension);
        }

        return messageBuilder.toString();
    }

    private Map<String, Object> getCustomHeader(EmailSendTarget emailSendTarget) {
        Map<String, Object> customHeader = new HashMap<>();

        // Create One-Click List-Unsubscribe header
        if (emailConfig.isUnsubscribeHeaderEnabled() && "AD".equals(emailSendContext.messageType())) {
            customHeader.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            String rejectUrl = emailSendTarget.getAttributeString(EmailSendTarget.ATTR_REJECT_URL);
            if (rejectUrl != null && !rejectUrl.isBlank()) {
                customHeader.put("List-Unsubscribe", "<" + rejectUrl + ">");
            }
        }

        return customHeader;
    }

}
