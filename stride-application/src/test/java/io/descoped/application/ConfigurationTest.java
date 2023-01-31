package io.descoped.application;

import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigurationTest {

    @Test
    void testConfiguration() {
        Configuration configuration = Configuration.create(ApplicationProperties.builder()
                .testDefaults()
                .build());
        assertEquals("default", configuration.alias());
        assertEquals("0.0.1", configuration.version());
        assertEquals("localhost", configuration.host());
        assertEquals(0, configuration.port());
    }
}
