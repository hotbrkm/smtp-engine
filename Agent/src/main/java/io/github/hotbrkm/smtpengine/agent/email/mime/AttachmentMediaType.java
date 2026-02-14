package io.github.hotbrkm.smtpengine.agent.email.mime;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Email attachment media type
 */
@RequiredArgsConstructor
@Getter
public enum AttachmentMediaType {
    UPLOAD("UPLOAD"), PATH("PATH");

    private final String code;

    public static Optional<AttachmentMediaType> fromCode(String rawCode) {
        if (rawCode == null) {
            return Optional.empty();
        }

        String normalized = rawCode.trim();
        return Arrays.stream(values())
                .filter(type -> type.code.equalsIgnoreCase(normalized))
                .findFirst();
    }
}
