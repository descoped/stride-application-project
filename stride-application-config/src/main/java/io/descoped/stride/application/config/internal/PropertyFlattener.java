package io.descoped.stride.application.config.internal;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * The inverse of {@link PropertyTree}: turns a JSON tree into flat properties. Object fields are joined with
 * {@code '.'} and array elements get their index as a segment. Empty objects, empty arrays and nulls have no
 * property form and are left out.
 */
public final class PropertyFlattener {

    private PropertyFlattener() {
    }

    public static void flatten(String key, JsonNode node, Map<String, String> properties) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> flatten(key + "." + field.getKey(), field.getValue(), properties));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(key + "." + i, node.get(i), properties);
            }
        } else {
            properties.put(key, node.asText());
        }
    }
}
