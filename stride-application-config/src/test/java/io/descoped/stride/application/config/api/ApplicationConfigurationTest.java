package io.descoped.stride.application.config.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.descoped.stride.application.utils.api.ClassPathResourceUtils;
import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationConfigurationTest {
    private static final Logger log = LoggerFactory.getLogger(ApplicationConfigurationTest.class);

    @Test
    void testApplicationConfiguration() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .configuration(ApplicationProperties.builder()
                        .testDefaults())
                .build();
        log.trace("{}", configuration.toPrettyString());

        assertEquals("localhost", configuration.server().host());
        assertEquals(9090, configuration.server().port());

        assertEquals("default", configuration.application().alias());
        assertEquals("unknown", configuration.application().version());
        assertEquals("localhost", configuration.server().host());
        assertEquals(9090, configuration.server().port());

        log.trace("server.host: {}", configuration.server().host());
        log.trace("server.port: {}", configuration.server().port());
        log.trace("server.contextPath: {}", configuration.server().contextPath());

        log.trace("application.alias: {}", configuration.application().alias());
        log.trace("application.version: {}", configuration.application().version());
        log.trace("application.url: {}", configuration.application().url());
        log.trace("application.cors.headers: {}", configuration.application().cors().headers());
    }

    @Test
    void newYamlToPropsConfig() throws IOException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String res = ClassPathResourceUtils.readResource("application-config-test.yaml");
        JsonNode root = mapper.readTree(res);
        JavaPropsMapper propsMapper = new JavaPropsMapper();
        log.trace("\n{}", propsMapper.writeValueAsString(root));
    }
}
