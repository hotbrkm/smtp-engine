package io.github.hotbrkm.smtpengine.agent.email.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailDomainManager update behavior test")
class EmailDomainManagerTest {

    @Test
    @DisplayName("updateEmailDomains replaces existing domains entirely")
    void updateEmailDomains_replacesExistingDomains() {
        LocalDateTime initTime = LocalDateTime.of(2026, 2, 9, 10, 0, 0);
        EmailDomainManager manager = new EmailDomainManager(
                List.of(new EmailDomain("a.com", 1, 10, 60, 60, "")),
                initTime
        );

        LocalDateTime updatedTime = LocalDateTime.of(2026, 2, 9, 11, 0, 0);
        manager.updateEmailDomains(
                List.of(new EmailDomain("b.com", 3, 20, 30, 40, "")),
                updatedTime
        );

        assertThat(manager.getEmailDomainNames()).containsExactly("b.com");
        assertThat(manager.getEmailDomain("b.com").getSessionCount()).isEqualTo(3);
        assertThat(manager.getEmailDomain("a.com").getDomainName()).isEqualTo("default");
        assertThat(manager.getUpdateTime()).isEqualTo(updatedTime);
    }

    @Test
    @DisplayName("Domain lookup is case-insensitive")
    void getEmailDomain_isCaseInsensitive() {
        EmailDomainManager manager = new EmailDomainManager(
                List.of(new EmailDomain("Gmail.COM", 2, 30, 50, 55, "")),
                LocalDateTime.now()
        );

        EmailDomain domain = manager.getEmailDomain("gmail.com");
        assertThat(domain.getSessionCount()).isEqualTo(2);
        assertThat(domain.getSendCountPerSession()).isEqualTo(30);
    }

    @Test
    @DisplayName("When default domain exists, returns it for unregistered domain lookup")
    void getEmailDomain_returnsConfiguredDefaultDomain() {
        EmailDomainManager manager = new EmailDomainManager(
                List.of(new EmailDomain("default", 5, 77, 11, 22, "")),
                LocalDateTime.now()
        );

        EmailDomain fallback = manager.getEmailDomain("unknown-domain.com");
        assertThat(fallback.getDomainName()).isEqualTo("default");
        assertThat(fallback.getSessionCount()).isEqualTo(5);
        assertThat(fallback.getSendCountPerSession()).isEqualTo(77);
    }

    @Test
    @DisplayName("Per-IP session limit guarantees minimum of 1")
    void getSessionLimit_guaranteesMinimumOne() {
        EmailDomainManager manager = new EmailDomainManager(
                List.of(new EmailDomain("default", 0, 10, 60, 60, "")),
                LocalDateTime.now()
        );

        assertThat(manager.getSessionLimit("unknown-domain.com")).isEqualTo(1);
    }
}
