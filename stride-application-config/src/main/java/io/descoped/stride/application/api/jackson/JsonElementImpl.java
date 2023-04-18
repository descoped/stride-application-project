package io.descoped.stride.application.api.jackson;

import com.fasterxml.jackson.databind.JsonNode;

record JsonElementImpl(JsonNode json, JsonCreationStrategy strategy) implements JsonElement {

    JsonElementImpl(JsonNode json) {
        this(json, JsonCreationStrategy.STRICT);
    }
}
