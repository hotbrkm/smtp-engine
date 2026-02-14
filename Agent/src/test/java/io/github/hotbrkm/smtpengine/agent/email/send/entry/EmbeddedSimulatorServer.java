package io.github.hotbrkm.smtpengine.agent.email.send.entry;

import io.github.hotbrkm.smtpengine.simulator.config.SimulatorSmtpProperties;
import io.github.hotbrkm.smtpengine.simulator.smtp.handler.SimulatorMessageHandlerFactory;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicyOrchestrator;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicyOrchestratorFactory;
import io.github.hotbrkm.smtpengine.simulator.smtp.policy.PolicySessionHandler;
import io.github.hotbrkm.smtpengine.simulator.smtp.service.SmtpMessageStore;
import org.subethamail.smtp.server.SMTPServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

/**
 * Helper to run an embedded Simulator SMTP server for Agent tests.
 */
final class EmbeddedSimulatorServer implements AutoCloseable {

    private static final String HOST = "127.0.0.1";

    private final SMTPServer smtpServer;
    private final Path inboxDir;
    private final int port;

    private EmbeddedSimulatorServer(SMTPServer smtpServer, Path inboxDir, int port) {
        this.smtpServer = smtpServer;
        this.inboxDir = inboxDir;
        this.port = port;
    }

    static EmbeddedSimulatorServer startDefault(Path inboxDir) throws IOException {
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(false);
        return start(inboxDir, policySet);
    }

    static EmbeddedSimulatorServer startWithGreylisting(Path inboxDir, List<String> whitelistedRecipients) throws IOException {
        return startWithGreylisting(inboxDir, whitelistedRecipients, Duration.ofMillis(200));
    }

    static EmbeddedSimulatorServer startWithGreylisting(Path inboxDir, List<String> whitelistedRecipients, Duration retryMinDelay) throws IOException {
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        SimulatorSmtpProperties.PolicySet.Rule rule = new SimulatorSmtpProperties.PolicySet.Rule();
        rule.setEnabled(true);
        rule.setType(SimulatorSmtpProperties.PolicySet.RuleType.GREYLISTING);
        rule.setPhases(List.of("rcpt-pre"));
        rule.setOrder(100);

        SimulatorSmtpProperties.PolicySet.Greylisting greylisting = new SimulatorSmtpProperties.PolicySet.Greylisting();
        greylisting.setEnabled(true);
        greylisting.setRetryMinDelay(retryMinDelay == null ? Duration.ofMillis(200) : retryMinDelay);
        greylisting.setRetryMaxWindow(Duration.ofHours(1));
        greylisting.setPendingExpiration(Duration.ofMinutes(30));

        SimulatorSmtpProperties.PolicySet.Greylisting.Response response = new SimulatorSmtpProperties.PolicySet.Greylisting.Response();
        response.setCode(451);
        response.setEnhancedStatus("4.7.1");
        response.setMessage("Greylisting in action. Try again later. [key={key}]");
        greylisting.setResponse(response);

        SimulatorSmtpProperties.PolicySet.Greylisting.Bypass bypass = new SimulatorSmtpProperties.PolicySet.Greylisting.Bypass();
        bypass.setAuthenticated(false);
        bypass.setWhitelistedRecipients(normalizeRecipientPatterns(whitelistedRecipients));
        greylisting.setBypass(bypass);

        rule.setGreylisting(greylisting);
        policySet.setRules(List.of(rule));
        return start(inboxDir, policySet);
    }

    static EmbeddedSimulatorServer startWithPermanentRcptFailure(Path inboxDir, int smtpCode) throws IOException {
        return startWithFaultInjection(inboxDir, "rcpt-pre", smtpCode, false);
    }

    static EmbeddedSimulatorServer startWithFaultInjection(Path inboxDir, String phase, int smtpCode) throws IOException {
        return startWithFaultInjection(inboxDir, phase, smtpCode, false);
    }

    static EmbeddedSimulatorServer startWithFaultInjection(Path inboxDir, String phase, int smtpCode, boolean allowDisconnect) throws IOException {
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        SimulatorSmtpProperties.PolicySet.Rule rule = new SimulatorSmtpProperties.PolicySet.Rule();
        rule.setEnabled(true);
        rule.setType(SimulatorSmtpProperties.PolicySet.RuleType.FAULT_INJECTION);
        rule.setPhases(List.of(phase));
        rule.setOrder(100);

        SimulatorSmtpProperties.PolicySet.FaultInjection faultInjection = new SimulatorSmtpProperties.PolicySet.FaultInjection();
        faultInjection.setMode("windowed");
        faultInjection.setWindowSize(1);
        faultInjection.setSeed(1L);

        LinkedHashMap<Integer, Integer> distribution = new LinkedHashMap<>();
        distribution.put(smtpCode, 100);
        faultInjection.setDistribution(distribution);

        SimulatorSmtpProperties.PolicySet.FaultInjection.Actions actions = new SimulatorSmtpProperties.PolicySet.FaultInjection.Actions();
        actions.setAllowDelay(false);
        actions.setAllowDisconnect(allowDisconnect);
        faultInjection.setActions(actions);

        rule.setFaultInjection(faultInjection);
        policySet.setRules(List.of(rule));
        return start(inboxDir, policySet);
    }

    static EmbeddedSimulatorServer startWithResponseDelay(Path inboxDir, String phase, Duration delay) throws IOException {
        SimulatorSmtpProperties.PolicySet policySet = new SimulatorSmtpProperties.PolicySet();
        policySet.setEnabled(true);

        SimulatorSmtpProperties.PolicySet.Rule rule = new SimulatorSmtpProperties.PolicySet.Rule();
        rule.setEnabled(true);
        rule.setType(SimulatorSmtpProperties.PolicySet.RuleType.RESPONSE_DELAY);
        rule.setPhases(List.of(phase));
        rule.setOrder(100);

        SimulatorSmtpProperties.PolicySet.ResponseDelay responseDelay = new SimulatorSmtpProperties.PolicySet.ResponseDelay();
        responseDelay.setDelay(delay == null ? Duration.ZERO : delay);
        responseDelay.setJitter(Duration.ZERO);
        rule.setResponseDelay(responseDelay);

        policySet.setRules(List.of(rule));
        return start(inboxDir, policySet);
    }

    static String unusedServerAddress() throws IOException {
        return HOST + ":" + findFreePort();
    }

    String serverAddress() {
        return HOST + ":" + port;
    }

    long messageCount() throws IOException {
        return messageFiles().size();
    }

    long countMessagesForRecipient(String recipient) throws IOException {
        if (recipient == null || recipient.isBlank()) {
            return 0;
        }
        String token = recipient.trim();
        return messageFiles().stream()
                .map(path -> path.getFileName().toString())
                .filter(name -> name.contains(token))
                .count();
    }

    private List<Path> messageFiles() throws IOException {
        if (!Files.exists(inboxDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(inboxDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
    }

    @Override
    public void close() {
        smtpServer.stop();
    }

    private static EmbeddedSimulatorServer start(Path inboxDir, SimulatorSmtpProperties.PolicySet policySet) throws IOException {
        Files.createDirectories(inboxDir);
        int port = findFreePort();

        SimulatorSmtpProperties properties = new SimulatorSmtpProperties();
        properties.setEnabled(true);
        properties.setBindAddress(HOST);
        properties.setHostName("smtp.simulator");
        properties.setPort(port);
        properties.setStoreMessages(true);
        properties.setInboxDirectory(inboxDir.toString());
        properties.setPolicy(policySet);

        SmtpMessageStore messageStore = new SmtpMessageStore(properties);
        PolicyOrchestrator orchestrator = PolicyOrchestratorFactory.build(policySet, null);
        SimulatorMessageHandlerFactory handlerFactory = new SimulatorMessageHandlerFactory(properties, messageStore, orchestrator);
        SMTPServer smtpServer = new SMTPServer.Builder()
                .messageHandlerFactory(handlerFactory)
                .sessionHandler(new PolicySessionHandler(orchestrator))
                .port(port)
                .bindAddress(java.net.InetAddress.getByName(HOST))
                .hostName("smtp.simulator")
                .build();
        smtpServer.start();
        waitUntilReady(port, Duration.ofSeconds(3));

        return new EmbeddedSimulatorServer(smtpServer, inboxDir, port);
    }

    private static List<String> normalizeRecipientPatterns(List<String> recipients) {
        List<String> normalized = new ArrayList<>();
        if (recipients == null) {
            return normalized;
        }
        for (String recipient : recipients) {
            if (recipient == null || recipient.isBlank()) {
                continue;
            }
            String value = recipient.trim().toLowerCase();
            normalized.add(value);
            if (!value.startsWith("<") && !value.endsWith(">")) {
                normalized.add("<" + value + ">");
            }
        }
        return normalized;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static void waitUntilReady(int port, Duration timeout) throws IOException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        IOException lastError = null;

        while (System.nanoTime() < deadlineNanos) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, port), 200);
                return;
            } catch (IOException e) {
                lastError = e;
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for embedded simulator startup", interrupted);
                }
            }
        }

        throw new IOException("Embedded simulator did not start in time on port " + port, lastError);
    }
}
