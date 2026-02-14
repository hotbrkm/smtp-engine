package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Helper utilities for EmailSendRunner tests
 */
final class EmailSendRunnerTestHelper {

    private EmailSendRunnerTestHelper() {
    }

    /**
     * Creates an EmailDomainManager with empty domain configuration
     */
    static EmailDomainManager getEmailDomainManager() {
        return new EmailDomainManager(Collections.emptyList(), LocalDateTime.now());
    }

    /**
     * Builds test message data
     */
    static Map<String, Object> getMessage(long messageId) {
        Map<String, Object> message = new HashMap<>();
        message.put(SendRequestKey.MESSAGE_ID, messageId);
        message.put(SendRequestKey.TEMPLATE_SUBTYPE, "GENERAL");
        message.put(SendRequestKey.MESSAGE_TYPE, "INFO");
        return message;
    }

    /**
     * Builds test send request data
     */
    static Map<String, Object> getSendRequest(long messageId, long resultSeq, int groupSeq) {
        Map<String, Object> sendRequest = new HashMap<>();
        sendRequest.put(SendRequestKey.MESSAGE_ID, messageId);
        sendRequest.put(SendRequestKey.RESULT_SEQ, resultSeq);
        sendRequest.put(SendRequestKey.GROUP_SEQ, groupSeq);
        sendRequest.put(SendRequestKey.EXECUTION_MODE, ExecutionMode.BATCH.name());
        return sendRequest;
    }

    /**
     * Creates target data
     */
    static Map<String, Object> getTarget(String emailAddress, String userName) {
        Map<String, Object> target = new TrackingTargetMap();
        target.put(TargetKey.TARGET_EMAIL, emailAddress);
        target.put(TargetKey.TARGET_NAME, userName);
        target.put(TargetKey.TARGET_ID, "u-1");
        target.put(TargetKey.LIST_SEQ, 1);
        target.put(TargetKey.SENDER_EMAIL, "sender@example.com");
        target.put(TargetKey.SENDER_NAME, "Sender");
        target.put(TargetKey.TITLE, "Hello");
        target.put(TargetKey.BODY, "<p>Hi</p>");
        target.put(TargetKey.RETRY_COUNT, 0);
        target.put(TargetKey.SEND_STATUS, "PENDING");
        target.put(TargetKey.SEND_CODE, "701");

        return target;
    }

    /**
     * Converts target maps to list
     */
    @SafeVarargs
    static List<Map<String, Object>> toList(Map<String, Object>... targetMaps) {
        return new ArrayList<>(Arrays.asList(targetMaps));
    }

    @SafeVarargs
    static List<EmailSendTarget> toTargets(Map<String, Object>... targetMaps) {
        return Arrays.stream(targetMaps)
                .map(EmailSendRunnerTestHelper::toTarget)
                .toList();
    }

    static List<EmailSendTarget> toTargets(List<Map<String, Object>> targetMaps) {
        return targetMaps.stream()
                .map(EmailSendRunnerTestHelper::toTarget)
                .toList();
    }

    static EmailSendContext getContext(Map<String, Object> message, Map<String, Object> sendRequest, Map<String, Object> template) {
        String templateSubtype = asString(template.get(SendRequestKey.TEMPLATE_SUBTYPE));
        if (templateSubtype == null) {
            templateSubtype = asString(message.get(SendRequestKey.TEMPLATE_SUBTYPE));
        }
        String messageType = asString(message.get(SendRequestKey.MESSAGE_TYPE));
        long messageId = (long) sendRequest.get(SendRequestKey.MESSAGE_ID);
        long resultSeq = (long) sendRequest.get(SendRequestKey.RESULT_SEQ);
        int groupSeq = (int) sendRequest.get(SendRequestKey.GROUP_SEQ);
        ExecutionMode executionMode = ExecutionMode.valueOf((String) sendRequest.get(SendRequestKey.EXECUTION_MODE));

        return new EmailSendContext(messageId, resultSeq, groupSeq, messageType, templateSubtype, executionMode);
    }

    static EmailSendContext getContext(Map<String, Object> message, Map<String, Object> sendRequest) {
        return getContext(message, sendRequest, Collections.emptyMap());
    }

    private static EmailSendTarget toTarget(Map<String, Object> targetData) {
        String targetName = defaultIfNull(asString(targetData.get(TargetKey.TARGET_NAME)), "");
        String title = defaultIfNull(asString(targetData.get(TargetKey.TITLE)), "");
        String body = defaultIfNull(asString(targetData.get(TargetKey.BODY)), "");
        int retryCount = asInt(targetData.get(TargetKey.RETRY_COUNT), 0);
        String sendCode = asString(targetData.get(TargetKey.SEND_CODE));
        String sendStatus = asString(targetData.get(TargetKey.SEND_STATUS));
        String errorMessage = asString(targetData.get(TargetKey.ERROR_MESSAGE));
        String endDateTime = asString(targetData.get(TargetKey.END_DATETIME));
        String emailDomain = asString(targetData.get(TargetKey.EMAIL_DOMAIN));

        EmailSendTarget target = EmailSendTarget.builder()
                .targetId(asString(targetData.get(TargetKey.TARGET_ID)))
                .targetName(targetName)
                .targetEmail(asString(targetData.get(TargetKey.TARGET_EMAIL)))
                .senderName(asString(targetData.get(TargetKey.SENDER_NAME)))
                .senderEmail(asString(targetData.get(TargetKey.SENDER_EMAIL)))
                .title(title)
                .body(body)
                .coverBody(asString(targetData.get(TargetKey.COVER)))
                .rejectUrl(asString(targetData.get(TargetKey.REJECT_URL)))
                .listSeq((int) (targetData.get(TargetKey.LIST_SEQ)))
                .secuKey(asString(targetData.get(TargetKey.TARGET_SECU_KEY)))
                .retryCount(retryCount)
                .sendCode(sendCode)
                .sendStatus(sendStatus)
                .errorMessage(errorMessage)
                .endDateTime(endDateTime)
                .emailDomain(emailDomain)
                .targetData(targetData)
                .build();

        if (targetData instanceof TrackingTargetMap trackingTargetMap) {
            trackingTargetMap.bind(target);
        }
        return target;
    }

    private static String asString(Object value) {
        return Objects.toString(value, null);
    }

    private static String defaultIfNull(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static final class TrackingTargetMap extends HashMap<String, Object> {
        private transient EmailSendTarget target;

        void bind(EmailSendTarget target) {
            this.target = target;
        }

        @Override
        public Object get(Object key) {
            if (!(key instanceof String keyString) || target == null) {
                return super.get(key);
            }

            return switch (keyString) {
                case TargetKey.SEND_CODE -> target.getSendCode();
                case TargetKey.SEND_STATUS -> target.getSendStatus();
                case TargetKey.ERROR_MESSAGE -> target.getErrorMessage();
                case TargetKey.END_DATETIME -> target.getEndDateTime();
                case TargetKey.EMAIL_DOMAIN -> target.getEmailDomain();
                case TargetKey.RETRY_COUNT -> target.getRetryCount();
                default -> super.get(key);
            };
        }
    }
}
