package io.descoped.stride.application.jackson;

import com.fasterxml.jackson.databind.JsonNode;

record JsonElementImpl(JsonNode json, JsonCreationStrategy strategy) implements JsonElement {

    JsonElementImpl(JsonNode json) {
        this(json, JsonCreationStrategy.FAIL_FAST);
    }
}
