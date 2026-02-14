package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailAddressUtil;
import io.github.hotbrkm.smtpengine.agent.email.mime.AttachmentMedia;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailSendProgress;
import io.github.hotbrkm.smtpengine.agent.email.send.result.SendResult;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
public final class EmailSendTarget {
    public static final String ATTR_LIST_SEQ = "listSeq";
    public static final String ATTR_COVER_BODY = "coverBody";
    public static final String ATTR_REJECT_URL = "rejectUrl";
    public static final String ATTR_SECU_KEY = "secuKey";

    private final String targetId;
    private final String targetName;
    private final String targetEmail;
    private final String senderName;
    private final String senderEmail;
    private final String title;
    private final String body;
    private final Map<String, Object> attributes;
    private final List<AttachmentMedia> attachments;
    private final String domain;

    private int retryCount;
    private String sendCode;
    private String sendStatus;
    private String errorMessage;
    private String endDateTime;
    private String emailDomain;

    @Builder
    public EmailSendTarget(String targetEmail, String senderName, String senderEmail, String targetId, int listSeq,
                           String targetName, String title, String body, String coverBody, String rejectUrl,
                           String secuKey, int retryCount, String sendCode, String sendStatus, String errorMessage,
                           String endDateTime, String emailDomain, Map<String, Object> targetData,
                           List<AttachmentMedia> attachments) {
        this.targetEmail = targetEmail;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.targetId = targetId;
        this.targetName = targetName;
        this.title = title;
        this.body = body;

        Map<String, Object> attrMap = new HashMap<>();
        if (targetData != null) {
            attrMap.putAll(targetData);
        }
        attrMap.put(ATTR_LIST_SEQ, listSeq);
        if (coverBody != null) {
            attrMap.put(ATTR_COVER_BODY, coverBody);
        }
        if (rejectUrl != null) {
            attrMap.put(ATTR_REJECT_URL, rejectUrl);
        }
        if (secuKey != null) {
            attrMap.put(ATTR_SECU_KEY, secuKey);
        }
        this.attributes = attrMap;

        this.attachments = attachments == null ? List.of() : List.copyOf(attachments);
        this.retryCount = Math.max(retryCount, 0);
        this.sendCode = sendCode;
        this.sendStatus = sendStatus;
        this.errorMessage = errorMessage;
        this.endDateTime = endDateTime;
        this.emailDomain = emailDomain;
        this.domain = EmailAddressUtil.extractDomain(targetEmail);
    }

    /**
     * API for retrieving additional attributes.
     */
    public Object getAttribute(String key) {
        if (attributes == null || key == null) {
            return null;
        }
        return attributes.get(key);
    }

    /**
     * Retrieves a string value from additional attributes (Map).
     */
    public String getAttributeString(String key) {
        if (attributes == null || key == null) {
            return null;
        }
        Object v = attributes.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /**
     * Retrieves a string attribute from the original payload, returning a default value if missing.
     */
    public String getAttributeString(String key, String def) {
        String v = getAttributeString(key);
        return v == null ? def : v;
    }

    /**
     * Retrieves an integer attribute from the original payload.
     */
    public Integer getAttributeInt(String key) {
        if (attributes == null || key == null) {
            return null;
        }
        Object v = attributes.get(key);
        if (v instanceof Integer i) {
            return i;
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    /**
     * Retrieves an integer attribute from the original payload, returning a default value if missing or conversion fails.
     */
    public int getAttributeInt(String key, int def) {
        Integer v = getAttributeInt(key);
        return v == null ? def : v;
    }

    /**
     * Returns whether an attribute exists.
     */
    public boolean hasAttribute(String key) {
        return attributes != null && key != null && attributes.containsKey(key);
    }

    /**
     * Removes a derived attribute that is no longer needed during internal processing.
     */
    public void removeAttribute(String key) {
        if (attributes == null || key == null) {
            return;
        }
        attributes.remove(key);
    }

    /**
     * Reflects the sending result in a typed state.
     */
    public void applySendResult(SendResult sendResult, String endDateTime, String emailDomain) {
        this.sendCode = String.valueOf(sendResult.statusCode());
        this.sendStatus = sendResult.success() ? "SUCCESS" : "FAILURE";
        this.endDateTime = endDateTime;
        this.emailDomain = emailDomain;
        this.errorMessage = sendResult.success() ? null : sendResult.errorMessage();
    }

    /**
     * Increments the retry count by 1. (Sets to 1 if missing)
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * Read-only view of additional attributes (Map).
     */
    public Map<String, Object> attributesView() {
        return attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
    }

    public boolean isUnprocessed() {
        return sendCode == null || "701".equals(sendCode);
    }

    public int retryCount() {
        return retryCount;
    }

    public EmailSendProgress toProgress() {
        return new EmailSendProgress(getAttributeInt(ATTR_LIST_SEQ, 0), targetEmail, sendCode, sendStatus, errorMessage, endDateTime, emailDomain, retryCount);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (EmailSendTarget) obj;
        return Objects.equals(this.targetEmail, that.targetEmail)
                && Objects.equals(this.senderName, that.senderName)
                && Objects.equals(this.senderEmail, that.senderEmail)
                && Objects.equals(this.targetId, that.targetId)
                && Objects.equals(this.targetName, that.targetName)
                && Objects.equals(this.title, that.title)
                && Objects.equals(this.body, that.body)
                && Objects.equals(this.attributes, that.attributes)
                && Objects.equals(this.attachments, that.attachments)
                && this.retryCount == that.retryCount
                && Objects.equals(this.sendCode, that.sendCode)
                && Objects.equals(this.sendStatus, that.sendStatus)
                && Objects.equals(this.errorMessage, that.errorMessage)
                && Objects.equals(this.endDateTime, that.endDateTime)
                && Objects.equals(this.emailDomain, that.emailDomain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetEmail, senderName, senderEmail, targetId, targetName, title, body,
                attributes, attachments, retryCount, sendCode, sendStatus, errorMessage, endDateTime, emailDomain);
    }

    @Override
    public String toString() {
        return "SmtpRequest["
                + "rcptEmail=" + targetEmail + ", "
                + "senderName=" + senderName + ", "
                + "senderEmail=" + senderEmail + ", "
                + "targetId=" + targetId + ", "
                + "listSeq=" + getAttributeInt(ATTR_LIST_SEQ, 0) + ", "
                + "targetName=" + targetName + ", "
                + "title=" + title + ", "
                + "body=" + (body != null ? "<" + body.length() + " chars>" : null) + ", "
                + "rejectUrl=" + getAttributeString(ATTR_REJECT_URL) + ", "
                + "retryCount=" + retryCount + ", "
                + "sendCode=" + sendCode + ", "
                + "sendStatus=" + sendStatus + ']';
    }

}

