package io.descoped.stride.application;

import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class StrideApplicationTest {

    private static final Logger log = LoggerFactory.getLogger(StrideApplicationTest.class);

    @Test
    void testBootstrap() {
        ApplicationProperties properties = ApplicationProperties.builder()
                .classpathPropertiesFile("application-defaults.properties")
                .testDefaults()
                .build();

        try (StrideApplication application = new StrideApplication(properties)) {
            application.start();

        }
    }
}
