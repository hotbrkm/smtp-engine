package io.github.hotbrkm.smtpengine.agent.email.send.transport.dns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.TextParseException;

import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class DnsClientTest {

    public static final String DOMAIN = "example.com";
    public static final String NOT_EXIST_DOMAIN = "nonexistent.example";
    public static final String INVALID_DNS_SERVER_HOST = "invalid-dns-server.invalid";

    public static final String[] DNS_SERVER = {"8.8.8.8"};
    public static final String[] MULTIPLE_DNS_SERVER = {INVALID_DNS_SERVER_HOST, "8.8.8.8"};
    public static final String[] NOT_EXIST_SERVER = {INVALID_DNS_SERVER_HOST};

    @DisplayName("Should throw exception when DNS server list is empty")
    @Test
    void testConstructorWithEmptyDnsServer() {
        assertThrows(IllegalArgumentException.class, () -> new DnsClient(List.of()));
    }

    @DisplayName("Should throw exception when DNS server list contains only blank values")
    @Test
    void testConstructorWithBlankDnsServer() {
        assertThrows(IllegalArgumentException.class, () -> new DnsClient(List.of(" ", "")));
    }

    @DisplayName("Should resolve IP addresses by domain")
    @Test
    void testResolveDomainToIpAddresses() {
        // given
        DnsClient dnsClient = new DnsClient(List.of(DNS_SERVER));

        // when
        DnsQueryResult queryResult = dnsClient.resolveDomainToIpAddresses(DOMAIN);

        // then
        assertTrue(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertFalse(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should resolve IP addresses for non-existent domain")
    @Test
    void testResolveDomainToIpAddressesWithNotExistDomain() {
        // given
        DnsClient dnsClient = new DnsClient(List.of(DNS_SERVER));

        // when
        DnsQueryResult queryResult = dnsClient.resolveDomainToIpAddresses(NOT_EXIST_DOMAIN);

        // then
        assertFalse(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertTrue(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should resolve IP addresses for non-existent server")
    @Test
    void testResolveDomainToIpAddressesWithNotExistServer() {
        // given
        DnsClient dnsClient = new DnsClient(List.of(NOT_EXIST_SERVER));
        dnsClient.setRetryCount(1);

        // when
        DnsQueryResult queryResult = dnsClient.resolveDomainToIpAddresses(DOMAIN);

        // then
        assertFalse(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertTrue(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should resolve IP addresses from multiple servers")
    @Test
    void testResolveDomainToIpAddressesWithMultipleServer() {
        // given
        DnsClient dnsClient = new DnsClient(List.of(MULTIPLE_DNS_SERVER));
        dnsClient.setRetryCount(1);

        // when
        DnsQueryResult queryResult = dnsClient.resolveDomainToIpAddresses(DOMAIN);

        // then
        assertTrue(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertFalse(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should query MX records by domain")
    @Test
    void testQueryMxRecords() throws UnknownHostException, TextParseException {
        // given
        DnsClient dnsClient = new DnsClient(List.of(DNS_SERVER));

        // when
        DnsQueryResult queryResult = dnsClient.queryMxRecords(DOMAIN);

        // then
        assertTrue(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertFalse(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should query MX records for non-existent domain")
    @Test
    void testQueryMxRecordsWithNotExistDomain() throws UnknownHostException, TextParseException {
        // given
        DnsClient dnsClient = new DnsClient(List.of(DNS_SERVER));

        // when
        DnsQueryResult queryResult = dnsClient.queryMxRecords(NOT_EXIST_DOMAIN);

        // then
        assertFalse(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertTrue(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should query MX records from non-existent server")
    @Test
    void testQueryMxRecordsWithNotExistServer() throws UnknownHostException, TextParseException {
        // given
        DnsClient dnsClient = new DnsClient(List.of(NOT_EXIST_SERVER));
        dnsClient.setRetryCount(1);

        // when
        DnsQueryResult queryResult = dnsClient.queryMxRecords(DOMAIN);

        // then
        assertFalse(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertTrue(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should query MX records from multiple servers")
    @Test
    void testQueryMxRecordsWithMultipleServer() throws UnknownHostException, TextParseException {
        // given
        DnsClient dnsClient = new DnsClient(List.of(MULTIPLE_DNS_SERVER));
        dnsClient.setRetryCount(1);

        // when
        DnsQueryResult queryResult = dnsClient.queryMxRecords(DOMAIN);

        // then
        assertTrue(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertFalse(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should query A records by domain")
    @Test
    void testQueryARecords() throws UnknownHostException, TextParseException {
        // given
        DnsClient dnsClient = new DnsClient(List.of(DNS_SERVER));

        // when
        DnsQueryResult queryResult = dnsClient.queryARecords(DOMAIN);

        // then
        assertTrue(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertFalse(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should query A records for non-existent domain")
    @Test
    void testQueryARecordsWithNotExistDomain() throws UnknownHostException, TextParseException {
        // given
        DnsClient dnsClient = new DnsClient(List.of(DNS_SERVER));

        // when
        DnsQueryResult queryResult = dnsClient.queryARecords(NOT_EXIST_DOMAIN);

        // then
        assertFalse(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertTrue(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should query A records from non-existent server")
    @Test
    void testQueryARecordsWithNotExistServer() throws UnknownHostException, TextParseException {
        // given
        DnsClient dnsClient = new DnsClient(List.of(NOT_EXIST_SERVER));
        dnsClient.setRetryCount(1);

        // when
        DnsQueryResult queryResult = dnsClient.queryARecords(DOMAIN);

        // then
        assertFalse(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertTrue(queryResult.getRecords().isEmpty());
    }

    @DisplayName("Should query A records from multiple servers")
    @Test
    void testQueryARecordsWithMultipleServer() throws UnknownHostException, TextParseException {
        // given
        DnsClient dnsClient = new DnsClient(List.of(MULTIPLE_DNS_SERVER));
        dnsClient.setRetryCount(1);

        // when
        DnsQueryResult queryResult = dnsClient.queryARecords(DOMAIN);

        // then
        assertTrue(queryResult.isSuccess());
        assertFalse(queryResult.getMessage().isEmpty());
        assertFalse(queryResult.getRecords().isEmpty());
    }

}
