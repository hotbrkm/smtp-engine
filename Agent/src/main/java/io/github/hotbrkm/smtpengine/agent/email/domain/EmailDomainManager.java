package io.github.hotbrkm.smtpengine.agent.email.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * EmailDomainManager manages and provides email domain information.
 */
public class EmailDomainManager {

    private static final String DEFAULT = "default";
    private final Map<String, EmailDomain> emailDomainMap = new HashMap<>();
    private final EmailDomain defaultEmailDomain = new EmailDomain(DEFAULT, 10, 10, 60, 60, "");
    @Getter
    private LocalDateTime updateTime;

    /**
     * Constructor: Stores the given list of EmailDomain objects in the map upon initialization.
     *
     * @param emailDomainList List of EmailDomain objects to initialize
     */
    public EmailDomainManager(List<EmailDomain> emailDomainList, LocalDateTime updateTime) {
        updateEmailDomains(emailDomainList, updateTime);
    }

    /**
     * Returns the EmailDomain object corresponding to the given domain name.
     * Returns the default EmailDomain if the domain name does not exist.
     *
     * @param domainName Domain name to search
     * @return EmailDomain object for the domain or default EmailDomain
     */
    public synchronized EmailDomain getEmailDomain(String domainName) {
        EmailDomain emailDomain = emailDomainMap.get(domainName == null ? null : domainName.toLowerCase(Locale.ROOT));

        if (emailDomain != null) {
            return emailDomain;
        }

        return emailDomainMap.getOrDefault(DEFAULT, defaultEmailDomain);
    }

    /**
     * Returns the session limit for each domain.
     * Ensures a minimum of 1 even if sessionCount is 0 or less.
     */
    public synchronized int getSessionLimit(String domainName) {
        return Math.max(1, getEmailDomain(domainName).getSessionCount());
    }

    /**
     * Updates existing data or adds new data using the given list of EmailDomain objects.
     *
     * @param emailDomainList List of EmailDomain objects to update
     */
    public synchronized void updateEmailDomains(List<EmailDomain> emailDomainList) {
        updateEmailDomains(emailDomainList, LocalDateTime.now());
    }

    /**
     * Replaces all data with the given list of EmailDomain objects.
     * Domains not in the new list are removed from the map, and updateTime is also updated.
     *
     * @param emailDomainList List of EmailDomain objects to replace
     * @param updateTime      Update time
     */
    public synchronized void updateEmailDomains(List<EmailDomain> emailDomainList, LocalDateTime updateTime) {
        List<EmailDomain> safeList = emailDomainList == null ? Collections.emptyList() : emailDomainList;
        emailDomainMap.clear();
        for (EmailDomain emailDomain : safeList) {
            emailDomainMap.put(emailDomain.getDomainName().toLowerCase(Locale.ROOT), emailDomain);
        }
        this.updateTime = updateTime != null ? updateTime : LocalDateTime.now();
    }

    /**
     * Returns the domain names of the stored EmailDomains.
     */
    public synchronized List<String> getEmailDomainNames() {
        return emailDomainMap.keySet().stream().toList();
    }
}
