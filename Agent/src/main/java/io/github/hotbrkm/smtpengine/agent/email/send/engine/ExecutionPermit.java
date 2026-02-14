package io.github.hotbrkm.smtpengine.agent.email.send.engine;

import io.github.hotbrkm.smtpengine.agent.email.send.engine.resource.BindIpLease;


/**
 * Represents a bundle of resources (bind IP lease, attachment slot) acquired before batch execution.
 */
record ExecutionPermit(BindIpLease lease, boolean attachmentSlotAcquired) {
}
