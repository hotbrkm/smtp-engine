package io.github.hotbrkm.smtpengine.simulator.smtp.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the SMTP Simulator server.
 * <p>
 * These properties are loaded from the {@code simulator.smtp} prefix in application.yml.
 * </p>
 *
 * <h2>Example Configuration</h2>
 * <pre>
 * simulator:
 *   smtp:
 *     enabled: true
 *     port: 2525
 *     host-name: smtp.example.com
 *     bind-address: 0.0.0.0
 *     max-connections: 1000
 * </pre>
 *
 * @author hotbrkm
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "simulator.smtp")
public class SimulatorSmtpProperties {

    /**
     * Whether the SMTP server is enabled.
     */
    private boolean enabled = true;

    /**
     * Port number for the SMTP server to listen on.
     */
    private int port = 2525;

    /**
     * Host name to use for the SMTP server.
     */
    private String hostName;

    /**
     * Address to bind the SMTP server to.
     */
    private String bindAddress;

    /**
     * Maximum number of concurrent connections allowed.
     */
    private Integer maxConnections;

    /**
     * Maximum message size in bytes.
     */
    private Integer maxMessageSize;

    /**
     * Directory path for storing received messages.
     */
    private String inboxDirectory;

    /**
     * Whether to store received messages to disk.
     */
    private boolean storeMessages = true;

    /**
     * Policy configuration for connection and message handling rules.
     */
    @NestedConfigurationProperty
    private PolicySet policy = new PolicySet();

    /**
     * Policy set containing all policy rules.
     */
    @Getter
    @Setter
    public static class PolicySet {

        /**
         * Whether policy enforcement is enabled.
         */
        private boolean enabled;

        /**
         * List of policy rules to apply.
         */
        private List<Rule> rules = new ArrayList<>();

        /**
         * Individual policy rule configuration.
         */
        @Getter
        @Setter
        public static class Rule {
            /**
             * Whether this rule is enabled.
             */
            private boolean enabled = true;

            /**
             * Type of policy rule.
             */
            private RuleType type = RuleType.RATE_LIMIT;

            /**
             * SMTP phases where this rule should be applied.
             */
            private List<String> phases = new ArrayList<>();

            /**
             * Execution order of this rule (lower = earlier).
             */
            private int order = 100;

            /** Connection limit configuration. */
            @NestedConfigurationProperty
            private ConnectionLimit connectionLimit = new ConnectionLimit();

            /** Rate limit configuration. */
            @NestedConfigurationProperty
            private RateLimit rateLimit = new RateLimit();

            /** Adaptive rate control configuration. */
            @NestedConfigurationProperty
            private AdaptiveRateControl adaptiveRateControl = new AdaptiveRateControl();

            /** Response delay configuration. */
            @NestedConfigurationProperty
            private ResponseDelay responseDelay = new ResponseDelay();

            /** Greylisting configuration. */
            @NestedConfigurationProperty
            private Greylisting greylisting = new Greylisting();

            /** Disconnect configuration. */
            @NestedConfigurationProperty
            private Disconnect disconnect = new Disconnect();

            /** Mail authentication check configuration. */
            @NestedConfigurationProperty
            private MailAuthCheck mailAuthCheck = new MailAuthCheck();

            /** Fault injection configuration. */
            @NestedConfigurationProperty
            private FaultInjection faultInjection = new FaultInjection();
        }

        /**
         * Enumeration of available policy rule types.
         */
        public enum RuleType {
            /** Limits concurrent connections per IP or globally. */
            CONNECTION_LIMIT,
            /** Limits recipients per time window. */
            RATE_LIMIT,
            /** Adaptive rate control based on failure rates. */
            ADAPTIVE_RATE_CONTROL,
            /** Adds delay to responses. */
            RESPONSE_DELAY,
            /** Temporary rejection with retry mechanism. */
            GREYLISTING,
            /** Forced disconnection based on conditions. */
            DISCONNECT,
            /** Email authentication verification (SPF/DKIM/DMARC). */
            MAIL_AUTH_CHECK,
            /** Injects faults for testing purposes. */
            FAULT_INJECTION
        }

        /**
         * SMTP response configuration.
         */
        @Getter
        @Setter
        public static class Reply {
            /** SMTP response code. */
            private Integer code;
            /** Enhanced status code. */
            private String enhancedStatus;
            /** Response message. */
            private String message;
            /** Whether to disconnect after sending this response. */
            private Boolean disconnect;
        }

        /**
         * Connection limit rule configuration.
         */
        @Getter
        @Setter
        public static class ConnectionLimit {
            /** Maximum concurrent connections per IP address. */
            private Integer perIpMaxConnections;
            /** Maximum total concurrent connections. */
            private Integer globalMaxConnections;
            /** Response when limit is exceeded. */
            @NestedConfigurationProperty
            private Reply onExceed = new Reply();
        }

        /**
         * Rate limit rule configuration.
         */
        @Getter
        @Setter
        public static class RateLimit {
            /** Scope of rate limiting (ip, mail-from-domain, rcpt-domain). */
            private String scope = "ip";
            /** Time window for rate limiting. */
            private Duration window = Duration.ofMinutes(1);
            /** Maximum recipients per window. */
            private Integer maxRcptPerWindow = 300;
            /** Response when rate limit is exceeded. */
            @NestedConfigurationProperty
            private Reply onExceed = new Reply();
        }

        /**
         * Adaptive rate control rule configuration.
         */
        @Getter
        @Setter
        public static class AdaptiveRateControl {
            /** Scope for tracking (ip+mail-from-domain). */
            private String scope = "ip+mail-from-domain";
            /** Observation window for statistics. */
            private Duration observeWindow = Duration.ofMinutes(5);
            /** Cooldown period after tier promotion. */
            private Duration cooldown = Duration.ofMinutes(10);
            /** Whether to include synthetic responses in statistics. */
            private boolean includeSynthetic;
            /** Tier configurations. */
            private List<Tier> tiers = new ArrayList<>();
            /** Response when rate limit is exceeded. */
            @NestedConfigurationProperty
            private Reply onLimit = new Reply();

            /**
             * Tier configuration for adaptive rate control.
             */
            @Getter
            @Setter
            public static class Tier {
                /** Tier name. */
                private String name;
                /** Time window for this tier. */
                private Duration window = Duration.ofMinutes(1);
                /** Maximum recipients per window for this tier. */
                private Integer maxRcptPerWindow;
                /** Conditions to enter this tier. */
                @NestedConfigurationProperty
                private EnterWhen enterWhen = new EnterWhen();
            }

            /**
             * Entry conditions for a tier.
             */
            @Getter
            @Setter
            public static class EnterWhen {
                /** Always enter this tier. */
                private Boolean always;
                /** Enter when soft-fail rate is greater than or equal to. */
                private Double softfailRateGte;
                /** Enter when hard-fail rate is greater than or equal to. */
                private Double hardfailRateGte;
                /** Enter when consecutive soft-fail count is greater than or equal to. */
                private Integer softfailConsecutiveGte;
                /** Enter when disconnect count is greater than or equal to. */
                private Integer disconnectCountGte;
            }
        }

        /**
         * Response delay rule configuration.
         */
        @Getter
        @Setter
        public static class ResponseDelay {
            /** Conditions for applying delay. */
            @NestedConfigurationProperty
            private ApplyWhen applyWhen = new ApplyWhen();
            /** Base delay duration. */
            private Duration delay = Duration.ZERO;
            /** Random jitter to add to delay. */
            private Duration jitter = Duration.ZERO;

            /**
             * Conditions for applying response delay.
             */
            @Getter
            @Setter
            public static class ApplyWhen {
                /** List of tiers to apply delay to. */
                private List<String> tierIn = new ArrayList<>();
            }
        }

        /**
         * Greylisting rule configuration.
         */
        @Getter
        @Setter
        public static class Greylisting {
            /** Whether greylisting is enabled. */
            private boolean enabled = true;
            /** Tracking key combination. */
            private String trackBy = "ip-mail-from-rcpt-to";
            /** Minimum delay before retry is allowed. */
            private Duration retryMinDelay = Duration.ofMinutes(5);
            /** Maximum window for retry. */
            private Duration retryMaxWindow = Duration.ofHours(4);
            /** Duration to maintain whitelist after passing. */
            private Duration whitelistDuration = Duration.ofDays(36);
            /** Expiration time for pending entries. */
            private Duration pendingExpiration = Duration.ofHours(4);
            /** Response configuration. */
            @NestedConfigurationProperty
            private Response response = new Response();
            /** Bypass conditions. */
            @NestedConfigurationProperty
            private Bypass bypass = new Bypass();

            /**
             * Greylisting response configuration.
             */
            @Getter
            @Setter
            public static class Response {
                /** SMTP response code. */
                private Integer code = 451;
                /** Enhanced status code. */
                private String enhancedStatus = "4.7.1";
                /** Response message. */
                private String message = "Greylisting in action. Try again later.";
            }

            /**
             * Greylisting bypass conditions.
             */
            @Getter
            @Setter
            public static class Bypass {
                /** Bypass for authenticated connections. */
                private Boolean authenticated = false;
                /** Whitelisted IP addresses. */
                private List<String> whitelistedIps;
                /** Whitelisted sender addresses. */
                private List<String> whitelistedSenders;
                /** Whitelisted recipient addresses. */
                private List<String> whitelistedRecipients;
                /** Whitelisted hostnames. */
                private List<String> whitelistedHostnames;
            }
        }

        /**
         * Disconnect rule configuration.
         */
        @Getter
        @Setter
        public static class Disconnect {
            /** Conditions for disconnection. */
            @NestedConfigurationProperty
            private DisconnectConditions conditions = new DisconnectConditions();
            /** Response to send before disconnect. */
            @NestedConfigurationProperty
            private Reply reply = new Reply();
            /** Whether to close connection immediately after reply. */
            private boolean closeAfterReply = true;

            /**
             * Disconnection conditions.
             */
            @Getter
            @Setter
            public static class DisconnectConditions {
                /** Blocked recipient domains. */
                private List<String> blockedDomains;
                /** Blocked IP addresses. */
                private List<String> blockedIps;
                /** Time windows for blocking. */
                private List<TimeWindow> timeWindows;
            }
        }

        /**
         * Time window configuration.
         */
        @Getter
        @Setter
        public static class TimeWindow {
            /** Start time in HH:mm format. */
            private String start;
            /** End time in HH:mm format. */
            private String end;

            /**
             * Validates the time window format.
             * @throws IllegalArgumentException if format is invalid
             */
            public void validate() {
                if (start == null || !start.matches("^\\d{2}:\\d{2}$")) {
                    throw new IllegalArgumentException("Invalid format for time-window.start: " + start);
                }
                if (end == null || !end.matches("^\\d{2}:\\d{2}$")) {
                    throw new IllegalArgumentException("Invalid format for time-window.end: " + end);
                }
            }
        }

        /**
         * Mail authentication check configuration (SPF/DKIM/DMARC).
         */
        @Getter
        @Setter
        public static class MailAuthCheck {
            /** Whether mail auth check is enabled. */
            private boolean enabled = true;
            /** Mode: lint (warn only) or enforce (reject on failure). */
            private String mode = "lint";
            /** Whether to add authentication results to message headers. */
            private boolean addAuthenticationResults;

            /** SPF verification configuration. */
            @NestedConfigurationProperty
            private Spf spf = new Spf();
            /** DKIM verification configuration. */
            @NestedConfigurationProperty
            private Dkim dkim = new Dkim();
            /** DMARC verification configuration. */
            @NestedConfigurationProperty
            private Dmarc dmarc = new Dmarc();

            /**
             * SPF verification configuration.
             */
            @Getter
            @Setter
            public static class Spf {
                /** Whether SPF verification is enabled. */
                private boolean enabled = true;
                /** Verification level: warn, tempfail, or reject. */
                private String level = "warn";
                /** Whether to verify IP matches SPF record. */
                private boolean verifyIpMatch;
            }

            /**
             * DKIM verification configuration.
             */
            @Getter
            @Setter
            public static class Dkim {
                /** Whether DKIM verification is enabled. */
                private boolean enabled = true;
                /** Verification level: warn, tempfail, or reject. */
                private String level = "warn";
            }

            /**
             * DMARC verification configuration.
             */
            @Getter
            @Setter
            public static class Dmarc {
                /** Whether DMARC verification is enabled. */
                private boolean enabled = true;
                /** Verification level: warn, tempfail, or reject. */
                private String level = "warn";
                /** Whether to only check record existence without alignment. */
                private boolean checkRecordOnly = true;
            }
        }

        /**
         * Fault injection rule configuration (for testing).
         */
        @Getter
        @Setter
        public static class FaultInjection {
            /** Mode: random or windowed. */
            private String mode = "random";
            /** Window size for windowed mode. */
            private Integer windowSize = 100;
            /** Random seed for reproducible results. */
            private Long seed;
            /** Distribution of response codes. */
            private Map<Integer, Integer> distribution = new LinkedHashMap<>();
            /** Actions configuration. */
            @NestedConfigurationProperty
            private Actions actions = new Actions();

            /**
             * Fault injection actions configuration.
             */
            @Getter
            @Setter
            public static class Actions {
                /** Allow disconnection on 421 responses. */
                private boolean allowDisconnect = true;
                /** Allow adding random delay to error responses. */
                private boolean allowDelay = true;
                /** Maximum delay for error responses. */
                private Duration maxDelay = Duration.ZERO;
            }
        }
    }
}
