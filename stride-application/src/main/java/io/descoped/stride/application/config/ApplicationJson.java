package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import no.cantara.config.ApplicationProperties;
import no.cantara.config.json.PropertyMapToJsonConverter;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public final class ApplicationJson {

    private final ApplicationProperties properties;
    private final JsonNode json;

    public ApplicationJson(String properties) {
        this(mapToApplicationProperties(propertiesToMap(properties)));
    }

    public ApplicationJson(ApplicationProperties properties) {
        this.properties = properties;
        PropertyMapToJsonConverter converter = new PropertyMapToJsonConverter(properties.map());
        json = converter.json();
    }

    public ApplicationProperties properties() {
        return properties;
    }

    public JsonNode json() {
        return json;
    }

    static Map<String, String> propertiesToMap(String properties) {
        Properties props = new Properties();
        try {
            props.load(new StringReader(properties));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String, String> map = props.entrySet().stream().collect(
                Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue()),
                        (prev, next) -> next, LinkedHashMap::new
                ));
        return map;
    }

    static ApplicationProperties mapToApplicationProperties(Map<String, String> map) {
        return ApplicationProperties.builder()
                .map(map)
                .build();
    }
}
