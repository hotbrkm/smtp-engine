package io.github.hotbrkm.smtpengine.agent.email.send.transport.network;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocketConfig {

    private String bindIp;
    private String serverIp;
    private int port;
    private int connectionTimeout;
    private int readTimeout;

    public SocketConfig(String bindIp, int connectionTimeout, int readTimeout) {
        this.bindIp = bindIp;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
    }

}
