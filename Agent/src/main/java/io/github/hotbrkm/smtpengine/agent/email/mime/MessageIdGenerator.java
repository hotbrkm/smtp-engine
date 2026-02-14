package io.github.hotbrkm.smtpengine.agent.email.mime;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MessageIdGenerator {
    private static final AtomicLong INDEX = new AtomicLong(0);
    private static volatile String hostName;

    public static String next() {
        long nextIndex = INDEX.updateAndGet(i -> (i + 1) % 10000000L);
        return "<" + System.currentTimeMillis() + "." + nextIndex + "@" + getHostName() + ">";
    }

    private static String getHostName() {
        String cached = hostName;
        if (cached != null) {
            return cached;
        }
        synchronized (MessageIdGenerator.class) {
            if (hostName != null) {
                return hostName;
            }
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                hostName = "127.0.0.1";
            }
            return hostName;
        }
    }
}
