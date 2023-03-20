package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;

record JsonElementImpl(JsonNode json, JsonElementStrategy strategy) implements JsonElement {

    JsonElementImpl(JsonNode json) {
        this(json, JsonElementStrategy.FAIL_FAST);
    }
}
