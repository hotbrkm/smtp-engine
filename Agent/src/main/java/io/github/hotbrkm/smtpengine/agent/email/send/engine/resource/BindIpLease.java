package io.github.hotbrkm.smtpengine.agent.email.send.engine.resource;

/**
 * Bind IP session slot lease information.
 */
public record BindIpLease(String domain, String bindIp, long acquiredAtMillis) {
}
