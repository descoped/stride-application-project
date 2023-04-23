package io.descoped.stride.application;

import io.descoped.stride.application.config.api.ApplicationConfiguration;
import io.descoped.stride.application.test.TestApplication;
import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;

public class TestApplicationTest {

    @Test
    void testApplication() {
        ApplicationProperties.Builder applicationProperties = ApplicationProperties.builder();

        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .configuration(applicationProperties);

        TestApplication application = new TestApplication.Builder()
                .configuration(configuration)
                .build();
    }
}
