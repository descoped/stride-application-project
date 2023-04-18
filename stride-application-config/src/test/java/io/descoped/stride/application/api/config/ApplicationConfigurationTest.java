package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.descoped.stride.application.api.utils.ClassPathResourceUtils;
import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationConfigurationTest {
    private static final Logger log = LoggerFactory.getLogger(ApplicationConfigurationTest.class);

    @Test
    void testApplicationConfiguration() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .configuration(ApplicationProperties.builder()
                        .testDefaults()
                        .build())
                .build();
        log.trace("{}", configuration.toPrettyString());

        assertEquals("localhost", configuration.asString("server.host", "example.com"));
        assertEquals(0, configuration.asInt("server.port", 9090));
        assertEquals("default", configuration.with("application.alias").asString("default"));

        assertEquals(configuration.asString("application.alias", "default"), configuration.application().alias());

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

    @Test
    void newYamlToPropsConfig() throws IOException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String res = ClassPathResourceUtils.readResource("app-config.yaml");
        JsonNode root = mapper.readTree(res);
        JavaPropsMapper propsMapper = new JavaPropsMapper();
        log.trace("\n{}", propsMapper.writeValueAsString(root));
    }


    @Test
    void traverseAppYaml() throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String res = ClassPathResourceUtils.readResource("app-config.yaml");
        JsonNode root = mapper.readTree(res);
        //log.trace("config:\n{}", root.toPrettyString());

        JavaPropsMapper propsMapper = new JavaPropsMapper();
        String props = propsMapper.writeValueAsString(root);
        log.trace("props:\n{}", props);
        Set<String> propsHierachySet = new ApplicationJson(props).keys("services");
        log.trace("p: {}", new TreeSet<>(propsHierachySet));

        Set<String> yamlHierachySet = new ApplicationJson(root).keys("services");
        log.trace("y: {}", new TreeSet<>(yamlHierachySet));

        assertEquals(propsHierachySet, yamlHierachySet);
    }
}