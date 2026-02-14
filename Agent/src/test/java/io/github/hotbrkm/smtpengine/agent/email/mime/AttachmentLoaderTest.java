package io.github.hotbrkm.smtpengine.agent.email.mime;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AttachmentLoader attachment loading verification")
class AttachmentLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Returns empty map when no attachments")
    void returnsEmptyMapWhenAttachmentListIsEmpty() throws Exception {
        // Given
        AttachmentLoader loader = createLoader();
        EmailSendTarget target = target(List.of());

        // When
        var result = loader.getAttachmentFiles(target);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Reads UPLOAD type attachment and returns fileName as key")
    void readsUploadAttachmentAndUsesFileNameAsKey() throws Exception {
        // Given
        byte[] content = "hello".getBytes();
        Path file = tempDir.resolve("sample.txt");
        Files.write(file, content);

        AttachmentMedia media = new AttachmentMedia(
                "sample.txt", file.toString());

        AttachmentLoader loader = createLoader();
        EmailSendTarget target = target(List.of(media));

        // When
        var result = loader.getAttachmentFiles(target);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get("sample.txt")).isEqualTo(content);
    }

    @Test
    @DisplayName("Reads PATH type attachment and returns it")
    void readsPathAttachment() throws Exception {
        // Given
        byte[] content = "path-content".getBytes();
        Path file = tempDir.resolve("doc.pdf");
        Files.write(file, content);

        AttachmentMedia media = new AttachmentMedia(
                "doc.pdf", file.toString());

        AttachmentLoader loader = createLoader();
        EmailSendTarget target = target(List.of(media));

        // When
        var result = loader.getAttachmentFiles(target);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get("doc.pdf")).isEqualTo(content);
    }

    @Test
    @DisplayName("Reads all multiple attachments")
    void readsMultipleAttachments() throws Exception {
        // Given
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "aaa");
        Files.writeString(file2, "bbb");

        List<AttachmentMedia> mediaList = List.of(
                new AttachmentMedia("a.txt", file1.toString()),
                new AttachmentMedia("b.txt", file2.toString())
        );

        AttachmentLoader loader = createLoader();
        EmailSendTarget target = target(mediaList);

        // When
        var result = loader.getAttachmentFiles(target);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsKeys("a.txt", "b.txt");
    }

    private AttachmentLoader createLoader() {
        return new AttachmentLoader();
    }

    private EmailSendTarget target(List<AttachmentMedia> attachments) {
        return EmailSendTarget.builder()
                .targetData(Collections.emptyMap())
                .attachments(attachments)
                .build();
    }
}
