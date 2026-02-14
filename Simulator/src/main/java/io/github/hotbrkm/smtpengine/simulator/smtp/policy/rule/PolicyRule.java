package io.github.hotbrkm.smtpengine.simulator.smtp.policy.rule;

import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyContext;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.PolicyOutcome;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.model.SmtpPhase;

/**
 * Interface for policy rules that evaluate SMTP sessions.
 * <p>
 * Policy rules are evaluated at various SMTP phases to determine
 * how to handle connections and messages. Each rule can allow,
 * deny, or delay responses based on its configuration.
 * </p>
 *
 * <h2>Implementation Example</h2>
 * <pre>{@code
 * public class MyRule implements PolicyRule {
 *     @Override
 *     public boolean supports(SmtpPhase phase) {
 *         return phase == SmtpPhase.RCPT_PRE;
 *     }
 *
 *     @Override
 *     public PolicyOutcome evaluate(PolicyContext context) {
 *         // Evaluate the policy
 *         return PolicyOutcome.allow();
 *     }
 * }
 * }</pre>
 *
 * @author hotbrkm
 * @since 1.0.0
 */
public interface PolicyRule {

    /**
     * Checks if this rule supports the given SMTP phase.
     * @param phase the SMTP phase to check
     * @return true if the rule supports this phase
     */
    default boolean supports(SmtpPhase phase) {
        return true;
    }

    /**
     * Evaluates this policy rule for the given context.
     * @param context the policy context containing session information
     * @return the policy outcome (allow, deny, or delay)
     */
    PolicyOutcome evaluate(PolicyContext context);

    /**
     * Called after all rules have been evaluated for a phase.
     * @param context the policy context
     * @param finalOutcome the final outcome after all rules
     */
    default void afterEvaluation(PolicyContext context, PolicyOutcome finalOutcome) {
        // no-op
    }

    /**
     * Called when the SMTP session ends.
     * @param context the policy context
     */
    default void onSessionEnd(PolicyContext context) {
        // no-op
    }
}
