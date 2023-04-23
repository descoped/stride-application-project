package io.descoped.stride.application.jackson.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.descoped.stride.application.jackson.api.JsonCreationStrategy;
import io.descoped.stride.application.jackson.api.JsonElement;

public record JsonElementImpl(JsonNode json, JsonCreationStrategy strategy) implements JsonElement {

    public JsonElementImpl(JsonNode json) {
        this(json, JsonCreationStrategy.STRICT);
    }
}
