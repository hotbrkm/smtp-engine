package io.github.hotbrkm.smtpengine.agent.email.send.planning;

import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendContext;
import io.github.hotbrkm.smtpengine.agent.email.send.result.EmailBatchResultWriter;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomain;
import io.github.hotbrkm.smtpengine.agent.email.domain.EmailDomainManager;
import io.github.hotbrkm.smtpengine.agent.email.send.entry.EmailSendTarget;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch planner
 * <p>
 * - Group input SmtpRequest list by domain
 * - Slice according to domain policy (send count per session)
 * - Create EmailBatchSubmitRequest list for engine submission
 */
@RequiredArgsConstructor
public class EmailBatchPlanner {

    private final String runnerId;
    private final List<EmailSendTarget> emailSendTargets;
    private final EmailDomainManager emailDomainManager;
    private final EmailSendContext emailSendContext;
    private final EmailBatchResultWriter resultWriter;

    /**
     * Groups targets by domain and creates submit requests by splitting into batch units according to policy
     *
     * @return Generated EmailBatchSubmitRequest list
     */
    public List<EmailBatchSpec> plan() {
        return createEmailBatchSpecs(groupByDomain());
    }

    /**
     * Groups target data by domain
     *
     * @return Map of SmtpRequest list with domain as key
     */
    private Map<String, List<EmailSendTarget>> groupByDomain() {
        Map<String, List<EmailSendTarget>> domainGroups = new LinkedHashMap<>();

        for (EmailSendTarget emailSendTarget : emailSendTargets) {
            String domain = emailSendTarget.getDomain();

            domainGroups.computeIfAbsent(domain, k -> new ArrayList<>()).add(emailSendTarget);
        }

        return domainGroups;
    }

    /**
     * Splits domain groups into batch units to create submit requests
     *
     * @param domainGroups SmtpRequest map grouped by domain
     * @return Generated EmailBatchSubmitRequest list
     */
    private List<EmailBatchSpec> createEmailBatchSpecs(Map<String, List<EmailSendTarget>> domainGroups) {
        List<EmailBatchSpec> batches = new ArrayList<>();
        int batchIndex = 0;

        for (Map.Entry<String, List<EmailSendTarget>> entry : domainGroups.entrySet()) {
            String domain = entry.getKey();
            List<EmailSendTarget> requests = entry.getValue();
            int batchSize = getBatchSizeForDomain(domain);

            for (int i = 0; i < requests.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, requests.size());
                List<EmailSendTarget> emailSendTargets = new ArrayList<>(requests.subList(i, endIndex));

                String batchId = runnerId + "-batch-" + (batchIndex++);
                batches.add(new EmailBatchSpec(batchId, domain, emailSendTargets, runnerId, resultWriter, emailSendContext));
            }
        }

        return batches;
    }

    /**
     * Gets batch size for domain
     *
     * @param domain Domain name
     * @return Send count per session
     */
    private int getBatchSizeForDomain(String domain) {
        EmailDomain emailDomain = emailDomainManager.getEmailDomain(domain);
        int size = emailDomain.getSendCountPerSession();
        return size > 0 ? size : 1;
    }
}
