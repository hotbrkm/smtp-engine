package io.github.hotbrkm.smtpengine.agent.email.send.transport.network;

import lombok.Getter;

@Getter
public class IpAddress {

    private static final String COLON = ":";

    private String ip;
    private int port;

    public IpAddress(String ip, int port) {
        if (ip.contains(COLON)) {
            parseIPAndPort(ip);
        } else {
            this.ip = ip;
            this.port = port;
        }

        this.ip = cleanIp(this.ip);
    }

    private void parseIPAndPort(String ipWithPort) {
        String[] ipAndPort = ipWithPort.split(COLON);
        this.ip = ipAndPort[0];
        this.port = Integer.parseInt(ipAndPort[1]);
    }

    private String cleanIp(String ip) {
        String cleanIp;

        if (ip.endsWith(".")) {
            cleanIp = ip.substring(0, ip.length() - 1);
        } else {
            cleanIp = ip;
        }

        return cleanIp;
    }

    @Override
    public String toString() {
        return ip + ":" + port;
    }
}
