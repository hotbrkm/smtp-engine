package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.properties.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FaultInjectionRule Test")
class FaultInjectionRuleTest {

    @DisplayName("Returns ALLOW when distribution is empty")
    @Test
    void testEvaluateAllowWhenDistributionIsMissing() {
        SimulatorSmtpProperties.PolicySet.FaultInjection config = new SimulatorSmtpProperties.PolicySet.FaultInjection();
        config.setDistribution(new LinkedHashMap<>());

        FaultInjectionRule rule = new FaultInjectionRule(config);

        assertThat(rule.evaluate(null).decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    @DisplayName("Returns synthetic PERM_FAIL with 550 100% distribution")
    @Test
    void testEvaluateInjectsPermFailAndMarksSynthetic() {
        SimulatorSmtpProperties.PolicySet.FaultInjection config = baseConfig("random", 100L);
        config.setDistribution(new LinkedHashMap<>());
        config.getDistribution().put(550, 100);
        config.getActions().setAllowDisconnect(true);

        FaultInjectionRule rule = new FaultInjectionRule(config);

        var outcome = rule.evaluate(null);
        assertThat(outcome.decision()).isEqualTo(PolicyDecision.PERM_FAIL);
        assertThat(outcome.smtpCode()).isEqualTo(550);
        assertThat(outcome.synthetic()).isTrue();
    }

    @DisplayName("Returns DISCONNECT/TEMP_FAIL based on allow-disconnect setting for 421 injection")
    @Test
    void testEvaluateDisconnectOnlyWhenAllowed() {
        SimulatorSmtpProperties.PolicySet.FaultInjection config = baseConfig("random", 1L);
        config.setDistribution(new LinkedHashMap<>());
        config.getDistribution().put(421, 100);

        config.getActions().setAllowDisconnect(true);
        FaultInjectionRule disconnectRule = new FaultInjectionRule(config);
        assertThat(disconnectRule.evaluate(null).decision()).isEqualTo(PolicyDecision.DISCONNECT);

        config.getActions().setAllowDisconnect(false);
        FaultInjectionRule tempFailRule = new FaultInjectionRule(config);
        assertThat(tempFailRule.evaluate(null).decision()).isEqualTo(PolicyDecision.TEMP_FAIL);
    }

    @DisplayName("Applies delay within max-delay range when allow-delay=true")
    @Test
    void testEvaluateAppliesRandomDelayWhenConfigured() {
        SimulatorSmtpProperties.PolicySet.FaultInjection config = baseConfig("random", 7L);
        config.setDistribution(new LinkedHashMap<>());
        config.getDistribution().put(451, 100);
        config.getActions().setAllowDelay(true);
        config.getActions().setMaxDelay(Duration.ofMillis(40));

        FaultInjectionRule rule = new FaultInjectionRule(config);

        var outcome = rule.evaluate(null);
        assertThat(outcome.decision()).isEqualTo(PolicyDecision.TEMP_FAIL);
        assertThat(outcome.delayMillis()).isBetween(0L, 40L);
    }

    @DisplayName("Windowed mode guarantees per-window distribution")
    @Test
    void testEvaluateHonorsWindowedDistribution() {
        SimulatorSmtpProperties.PolicySet.FaultInjection config = baseConfig("windowed", 12345L);
        config.setWindowSize(10);
        config.setDistribution(new LinkedHashMap<>());
        config.getDistribution().put(550, 20);

        FaultInjectionRule rule = new FaultInjectionRule(config);

        int failures = 0;
        for (int i = 0; i < 10; i++) {
            var outcome = rule.evaluate(null);
            if (outcome.decision() != PolicyDecision.ALLOW) {
                failures++;
            }
        }

        assertThat(failures).isEqualTo(2);
    }

    @DisplayName("Throws exception for invalid distribution code/sum")
    @Test
    void testConstructorRejectsInvalidDistribution() {
        SimulatorSmtpProperties.PolicySet.FaultInjection badCode = baseConfig("random", 1L);
        badCode.setDistribution(new LinkedHashMap<>());
        badCode.getDistribution().put(399, 10);

        assertThatThrownBy(() -> new FaultInjectionRule(badCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code must be 400-599");

        SimulatorSmtpProperties.PolicySet.FaultInjection badSum = baseConfig("random", 1L);
        badSum.setDistribution(new LinkedHashMap<>());
        badSum.getDistribution().put(451, 60);
        badSum.getDistribution().put(550, 50);

        assertThatThrownBy(() -> new FaultInjectionRule(badSum))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum must be <= 100");
    }

    private SimulatorSmtpProperties.PolicySet.FaultInjection baseConfig(String mode, Long seed) {
        SimulatorSmtpProperties.PolicySet.FaultInjection config = new SimulatorSmtpProperties.PolicySet.FaultInjection();
        config.setMode(mode);
        config.setSeed(seed);
        config.setActions(new SimulatorSmtpProperties.PolicySet.FaultInjection.Actions());
        return config;
    }
}
