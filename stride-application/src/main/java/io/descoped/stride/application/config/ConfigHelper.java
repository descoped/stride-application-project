package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ConfigHelper {

    public static ArrayNode createOrGet(ObjectNode builder, String field) {
        ArrayNode arrayNode;
        if (builder.has(field)) {
            arrayNode = (ArrayNode) builder.get(field);
        } else {
            arrayNode = JsonNodeFactory.instance.arrayNode();
            builder.set(field, arrayNode);
        }
        return arrayNode;
    }
}
