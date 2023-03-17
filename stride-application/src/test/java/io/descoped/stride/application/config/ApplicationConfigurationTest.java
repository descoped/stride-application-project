package io.descoped.stride.application.config;

import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationConfigurationTest {
    private static final Logger log = LoggerFactory.getLogger(ApplicationConfigurationTest.class);

    @Test
    void testApplicationConfiguration() {
        ApplicationJson jsonConfiguration = new ApplicationJson(ApplicationProperties.builder()
                .testDefaults()
                .build());

        ApplicationConfiguration configuration = new ApplicationConfiguration(jsonConfiguration.json());
        log.trace("{}", configuration.toPrettyString());

        assertEquals("localhost", configuration.element().with("server.host").asString("example.com"));
        assertEquals(0, configuration.element().with("server.port").asInt(9090));
        assertEquals("default", configuration.element().with("application.alias").asString("default"));

        assertEquals(configuration.with("application.alias").asString("default"), configuration.application().alias());

        assertEquals("default", configuration.application().alias());
        assertEquals("unknown", configuration.application().version());
        assertEquals("localhost", configuration.server().host());
        assertEquals(0, configuration.server().port());

        log.trace("server.host: {}", configuration.server().host());
        log.trace("server.port: {}", configuration.server().port());
        log.trace("server.contextPath: {}", configuration.server().contextPath());

        log.trace("application.alias: {}", configuration.application().alias());
        log.trace("application.version: {}", configuration.application().version());
        log.trace("application.url: {}", configuration.application().url());
        log.trace("application.cors.headers: {}", configuration.application().cors().headers());
    }
}
