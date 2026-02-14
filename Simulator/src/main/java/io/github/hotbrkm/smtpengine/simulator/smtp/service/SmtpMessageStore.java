package io.github.hotbrkm.smtpengine.simulator.smtp.service;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
public class SmtpMessageStore {

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final SimulatorSmtpProperties properties;
    private final Path inboxDirectory;

    public SmtpMessageStore(SimulatorSmtpProperties properties) {
        this.properties = properties;
        this.inboxDirectory = resolveInboxDirectory(properties);
    }

    @PostConstruct
    void prepareInboxDirectory() {
        if (!properties.isStoreMessages()) {
            log.info("SMTP message storage is disabled.");
            return;
        }

        try {
            Files.createDirectories(inboxDirectory);
            log.info("SMTP inbox directory initialized at {}", inboxDirectory.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create SMTP inbox directory: " + inboxDirectory, e);
        }
    }

    public void store(String from, String recipient, InputStream data) throws IOException {
        if (!properties.isStoreMessages()) {
            data.transferTo(OutputStream.nullOutputStream());
            return;
        }

        Path messagePath = inboxDirectory.resolve(generateFileName(recipient));
        try (OutputStream outputStream = Files.newOutputStream(messagePath, StandardOpenOption.CREATE_NEW)) {
            data.transferTo(outputStream);
        }

        log.info("SMTP mail received - from: {}, to: {}, stored: {}", from, recipient, messagePath.toAbsolutePath());
    }

    private Path resolveInboxDirectory(SimulatorSmtpProperties properties) {
        String inboxDir = properties.getInboxDirectory();
        if (inboxDir == null || inboxDir.isBlank()) {
            throw new IllegalStateException("Property 'simulator.smtp.inbox-directory' is required.");
        }
        return Paths.get(inboxDir);
    }

    private String generateFileName(String recipient) {
        String sanitizedRecipient = recipient == null ? "unknown" : recipient.replaceAll("[^a-zA-Z0-9@._-]", "_");
        String timestamp = FILE_NAME_FORMATTER.format(LocalDateTime.now());
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return timestamp + "-" + sanitizedRecipient + "-" + unique + ".eml";
    }
}
