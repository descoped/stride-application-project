package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import no.cantara.config.ApplicationProperties;
import no.cantara.config.json.PropertyMapToJsonConverter;

public final class ApplicationJson {

    private final ApplicationProperties properties;
    private final JsonNode json;

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
}
