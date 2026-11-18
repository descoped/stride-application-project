package io.descoped.stride.application.config;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationConfigurationTest {
    private static final Logger log = LoggerFactory.getLogger(ApplicationConfigurationTest.class);

    @Test
    void testApplicationConfiguration() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .build();
        log.trace("{}", configuration.toPrettyString());

        assertEquals("localhost", configuration.server().host());
        assertEquals(0, configuration.server().port());
        assertEquals("/", configuration.server().contextPath());

        assertEquals("default", configuration.application().alias());
        assertEquals("unknown", configuration.application().version());
        assertEquals(Optional.empty(), configuration.application().url());

        log.trace("server.host: {}", configuration.server().host());
        log.trace("server.port: {}", configuration.server().port());
        log.trace("server.contextPath: {}", configuration.server().contextPath());

        log.trace("application.alias: {}", configuration.application().alias());
        log.trace("application.version: {}", configuration.application().version());
        log.trace("application.url: {}", configuration.application().url());
    }
}
