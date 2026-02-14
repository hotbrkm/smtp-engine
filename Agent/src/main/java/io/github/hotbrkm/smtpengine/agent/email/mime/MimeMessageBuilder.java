package io.github.hotbrkm.smtpengine.agent.email.mime;

import jakarta.activation.DataHandler;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Getter
class MimeMessageBuilder {

    private static final String CRLF = "\r\n";
    private static final DateTimeFormatter RFC_2822_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
    private static final String ENC_BASE64 = "base64";
    private static final String ENC_QUOTED_PRINTABLE = "quoted-printable";

    private final Session session;
    private final MimeMessage mimeMessage;
    private final MimeMultipart alternativeContent;
    private final MimeMultipart mixedContent;

    private String from;
    private String subject;
    private String to;
    private String rcpt;
    private boolean isAlter = false;
    private boolean isMixed = false;

    private String charset;
    private String contentType;
    private String transferEncoding;
    private String headerWordEncoding = "B";
    private String extensionHeader;

    MimeMessageBuilder() {
        session = Session.getInstance(new Properties());
        mimeMessage = new MimeMessage(session);
        alternativeContent = new MimeMultipart("alternative");
        mixedContent = new MimeMultipart("mixed");
        charset = "UTF-8";
        transferEncoding = ENC_BASE64;
        setHeaderWordEncoding();
    }

    public void setFrom(String name, String email) {
        if (name != null) {
            try {
                from = "\"" + MimeUtility.fold(9, MimeUtility.encodeText(name.trim(), charset, headerWordEncoding)) + "\"";
            } catch (Exception ex) {
                from = "";
            }
        }

        if (email != null && !email.trim().isEmpty()) {
            from += " <" + email.trim() + ">";
        }
    }

    private void setHeaderWordEncoding() {
        if (transferEncoding.equals(ENC_QUOTED_PRINTABLE)) {
            headerWordEncoding = "Q";
        } else {
            headerWordEncoding = "B";
        }
    }

    public void setTo(String name, String email) {
        if (name != null) {
            try {
                to = "\"" + MimeUtility.fold(9, MimeUtility.encodeText(name.trim(), charset, headerWordEncoding)) + "\"";
            } catch (Exception ex) {
                to = "";
            }
        }

        if (!email.trim().isEmpty()) {
            to += " <" + email.trim() + ">";
        }
        rcpt = email;
    }

    public void setSubject(String subject) {
        try {
            this.subject = MimeUtility.fold(9, MimeUtility.encodeText(subject.trim(), charset, headerWordEncoding));
        } catch (Exception ex) {
            this.subject = "";
        }
    }

    public void setCharset(String charset) {
        if (charset != null) {
            this.charset = charset.toUpperCase();
        }
    }

    /**
     * Set MIME encoding type
     */
    public void setEncodingType(String encodingType) {
        switch (encodingType) {
            case "7bit", "8bit", "binary", ENC_QUOTED_PRINTABLE, ENC_BASE64 -> transferEncoding = encodingType;
            default -> transferEncoding = "8bit";
        }
        setHeaderWordEncoding();
    }

    /**
     * Set HTML body content
     */
    public void addAlterContent(String contentType, String content) throws Exception {
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(content, charset, getSubtype(contentType));
        bodyPart.setHeader("Content-Transfer-Encoding", ENC_BASE64);
        alternativeContent.addBodyPart(bodyPart);

        if (!isAlter) {
            MimeBodyPart alternativePart = new MimeBodyPart();
            alternativePart.setContent(alternativeContent);
            mixedContent.addBodyPart(alternativePart);
            isAlter = true;
        }

        this.contentType = contentType;
        transferEncoding = ENC_BASE64;
        setHeaderWordEncoding();
    }

    private String getSubtype(String contentType) {
        int slashIndex = contentType.indexOf('/');
        if (slashIndex != -1 && slashIndex < contentType.length() - 1) {
            return contentType.substring(slashIndex + 1);
        }
        return "html";
    }

    /**
     * Add attachment
     */
    public void addAttachment(String fileName, byte[] content) throws Exception {
        MimeBodyPart attachmentPart = new MimeBodyPart();

        String mimeType = getMimeType(fileName);
        attachmentPart.setDataHandler(new DataHandler(new ByteArrayDataSource(content, mimeType)));

        String encodedFileName = MimeUtility.encodeText(fileName.trim(), charset, "B");
        attachmentPart.setFileName(encodedFileName);
        attachmentPart.setHeader("Content-Disposition", "attachment; \r\n\tfilename=\"" + encodedFileName + "\"");

        mixedContent.addBodyPart(attachmentPart);
        isMixed = true;
    }

    private String getMimeType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return "application/octet-stream";
        }
        String extension = fileName.substring(dotIndex).toLowerCase();
        return switch (extension) {
            case ".txt" -> "text/plain";
            case ".html", ".htm" -> "text/html";
            case ".pdf" -> "application/pdf";
            case ".zip" -> "application/zip";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }

    public void addAttachments(Map<String, byte[]> attachmentFiles) throws Exception {
        for (Map.Entry<String, byte[]> entry : attachmentFiles.entrySet()) {
            addAttachment(entry.getKey(), entry.getValue());
        }
    }

    public void makeHeader(Map<String, Object> customHeader) throws Exception {
        mimeMessage.setHeader("Message-ID", MessageIdGenerator.next());
        mimeMessage.setHeader("From", from);
        mimeMessage.setHeader("To", to);
        mimeMessage.setHeader("Subject", subject);
        mimeMessage.setHeader("Date", ZonedDateTime.now().format(RFC_2822_FORMATTER));
        mimeMessage.setHeader("MIME-Version", "1.0");
        mimeMessage.setHeader("Precedence", "bulk");

        for (Map.Entry<String, Object> entry : customHeader.entrySet()) {
            mimeMessage.setHeader(entry.getKey(), entry.getValue().toString());
        }
    }

    public void makeBody() throws Exception {
        if (isMixed) {
            mimeMessage.setContent(mixedContent);
        } else if (isAlter) {
            mimeMessage.setContent(alternativeContent);
        }
        mimeMessage.saveChanges();
    }

    public void setExtension(String sHeaderLine) {
        this.extensionHeader = sHeaderLine;
    }

    public String getRcptDomain() {
        int pos = rcpt.indexOf("@");

        if (pos != -1) {
            String domain = rcpt.substring(pos + 1);
            return domain.toLowerCase();
        } else {
            return "";
        }
    }

    /**
     * Returns the MIME header and body as a String.
     */
    public String toString() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            mimeMessage.writeTo(outputStream);
            String result = outputStream.toString(Charset.forName(charset));

            if (extensionHeader != null) {
                return extensionHeader + CRLF + result;
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert MimeMessage to String", e);
        }
    }

}
