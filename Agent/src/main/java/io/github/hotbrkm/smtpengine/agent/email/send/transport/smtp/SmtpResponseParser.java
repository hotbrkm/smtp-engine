package io.github.hotbrkm.smtpengine.agent.email.send.transport.smtp;

import java.util.List;

class SmtpResponseParser {

    public SmtpResponse parseResponse(List<String> lines) {
        SmtpResponse smtpResponse = new SmtpResponse();

        for (String line : lines) {
            // If status code is not set and an abnormal response message exists, set status code to 888.
            // If status code is already set, ignore the abnormal response message.
            if (line == null || line.length() < 4) {
                if (smtpResponse.getStatusCode() <= 0) {
                    setInvalidResponseMessage(line, smtpResponse);
                }
                continue;
            }

            int statusCode = Integer.parseInt(line.substring(0, 3));
            String message = line.substring(4);
            message = message.trim();

            if (isMultiLineResponse(line)) {
                smtpResponse.addExtendedMessage(message);
            } else {
                smtpResponse.setStatusCode(statusCode);
                smtpResponse.setMessage(message);
                smtpResponse.setOriginalMessage(line);
            }
        }

        return smtpResponse;
    }

    private void setInvalidResponseMessage(String line, SmtpResponse smtpResponse) {
        smtpResponse.setStatusCode(888);
        smtpResponse.setMessage("response message is invalid. [" + line + "]");
        smtpResponse.setOriginalMessage("888 response message is invalid. [" + line + "]");
    }

    private boolean isMultiLineResponse(String line) {
        return line.charAt(3) == '-';
    }
}
