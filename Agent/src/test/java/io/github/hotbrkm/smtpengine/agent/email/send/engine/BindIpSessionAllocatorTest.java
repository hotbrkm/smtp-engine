package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomain;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpLease;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpSessionAllocator;
import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpCooldownPolicy;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BindIpSessionAllocator behavior verification")
class BindIpSessionAllocatorTest {

    @Test
    @DisplayName("Allocates bind IP with least usage first")
    void testAcquireLeastLoadedBindIp() {
        BindIpSessionAllocator allocator = new BindIpSessionAllocator(List.of("10.0.0.1", "10.0.0.2"), domainManager(1));

        BindIpLease lease1 = allocator.tryAcquire("example.com").orElseThrow();
        BindIpLease lease2 = allocator.tryAcquire("example.com").orElseThrow();

        assertThat(lease1.bindIp()).isNotEqualTo(lease2.bindIp());
        assertThat(allocator.tryAcquire("example.com")).isEmpty();

        allocator.release(lease1);
        BindIpLease lease3 = allocator.tryAcquire("example.com").orElseThrow();
        assertThat(lease3.bindIp()).isEqualTo(lease1.bindIp());
    }

    @Test
    @DisplayName("421 is immediately cooled down at default threshold (1 occurrence)")
    void testCooldownFor421Immediately() {
        BindIpCooldownPolicy cooldownPolicy = new BindIpCooldownPolicy(Set.of(421, 451), code -> code == 451 ? 2 : 1, 1_000L);
        BindIpSessionAllocator allocator = new BindIpSessionAllocator(List.of("10.0.0.1"), domainManager(10), cooldownPolicy);

        boolean cooled = allocator.recordBatchResult("example.com", "10.0.0.1", 421, false);

        assertThat(cooled).isTrue();
        assertThat(allocator.tryAcquire("example.com")).isEmpty();
    }

    @Test
    @DisplayName("451 is cooled down after 2 consecutive failures and consecutive count resets on success")
    void testCooldownThresholdAndResetFor451() {
        BindIpCooldownPolicy cooldownPolicy = new BindIpCooldownPolicy(Set.of(421, 451), code -> code == 451 ? 2 : 1, 1_000L);
        BindIpSessionAllocator allocator = new BindIpSessionAllocator(List.of("10.0.0.1"), domainManager(10), cooldownPolicy);

        boolean first = allocator.recordBatchResult("example.com", "10.0.0.1", 451, false);
        boolean resetBySuccess = allocator.recordBatchResult("example.com", "10.0.0.1", 451, true);
        boolean afterReset = allocator.recordBatchResult("example.com", "10.0.0.1", 451, false);
        boolean second = allocator.recordBatchResult("example.com", "10.0.0.1", 451, false);

        assertThat(first).isFalse();
        assertThat(resetBySuccess).isFalse();
        assertThat(afterReset).isFalse();
        assertThat(second).isTrue();
    }

    private EmailDomainManager domainManager(int sessionCount) {
        EmailDomain defaultDomain = new EmailDomain("default", sessionCount, 10, 60, 60, "");
        return new EmailDomainManager(List.of(defaultDomain), LocalDateTime.now());
    }
}
