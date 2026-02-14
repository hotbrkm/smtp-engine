package io.github.hotbrkm.smtpengine.simulator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@Slf4j
@SpringBootApplication(scanBasePackages = {"io.github.hotbrkm.smtpengine.simulator"})
public class SimulatorApplication {

    private static final String PROFILE_PROPERTY = "spring.profiles.active";
    private static final String DEFAULT_PROFILE = "default";

    public static void main(String[] args) {
        String profile = System.getProperty(PROFILE_PROPERTY, DEFAULT_PROFILE);
        
        log.info("Starting Simulator with profile: {}", profile);

        new SpringApplicationBuilder(SimulatorApplication.class)
                .profiles(profile)
                .run(args);
    }
}
