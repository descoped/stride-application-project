package io.descoped.stride.application.api.jackson.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.descoped.stride.application.api.jackson.JsonCreationStrategy;
import io.descoped.stride.application.api.jackson.JsonElement;

public record JsonElementImpl(JsonNode json, JsonCreationStrategy strategy) implements JsonElement {

    public JsonElementImpl(JsonNode json) {
        this(json, JsonCreationStrategy.STRICT);
    }
}
