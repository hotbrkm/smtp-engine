package io.github.hotbrkm.smtpengine.agent.email.send.result;

/**
 * Represents the SMTP transmission result.
 */
public record SendResult(boolean success, int statusCode, String errorMessage) {
    public static SendResult success(int code) {
        return new SendResult(true, code, null);
    }

    public static SendResult failure(int code, String msg) {
        return new SendResult(false, code, msg);
    }
}

