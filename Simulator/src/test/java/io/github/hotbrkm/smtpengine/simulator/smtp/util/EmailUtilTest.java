package io.github.hotbrkm.smtpengine.simulator.smtp.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailUtil Test")
class EmailUtilTest {

    @Test
    @DisplayName("Extracts domain from valid email address")
    void extractDomain_validEmail_returnsDomain() {
        // given
        String email = "user@example.com";

        // when
        String domain = EmailUtil.extractDomain(email);

        // then
        assertThat(domain).isEqualTo("example.com");
    }

    @Test
    @DisplayName("Converts uppercase domain to lowercase")
    void extractDomain_uppercaseDomain_returnsLowercase() {
        // given
        String email = "user@EXAMPLE.COM";

        // when
        String domain = EmailUtil.extractDomain(email);

        // then
        assertThat(domain).isEqualTo("example.com");
    }

    @ParameterizedTest
    @CsvSource({
            "user@sub.example.com, sub.example.com",
            "test@mail.co.kr, mail.co.kr",
            "admin@localhost, localhost"
    })
    @DisplayName("Correctly extracts domain from various domain formats")
    void extractDomain_variousDomains_returnsCorrectDomain(String email, String expectedDomain) {
        // when
        String domain = EmailUtil.extractDomain(email);

        // then
        assertThat(domain).isEqualTo(expectedDomain);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Returns null for null or empty string")
    void extractDomain_nullOrEmpty_returnsNull(String email) {
        // when
        String domain = EmailUtil.extractDomain(email);

        // then
        assertThat(domain).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"userexample.com", "user@", "@"})
    @DisplayName("Returns null for invalid email format")
    void extractDomain_invalidFormat_returnsNull(String email) {
        // when
        String domain = EmailUtil.extractDomain(email);

        // then
        assertThat(domain).isNull();
    }
}
