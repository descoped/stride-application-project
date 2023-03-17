package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

/**
 * The JsonElement is a convenient helper for Jackson ObjectNode, ArrayNode and ValueNode. The strategy declares
 * whether hierarchy is strictly respected or by using a more relaxed way of navigating null nodes. If using
 * the ephemeral node creation strategy, the with/at methods will create empty nodes in order to omit NPE during
 * traversal.
 */
public interface JsonElement {

    JsonNode json();

    JsonElementStrategy strategy();

    default Optional<JsonNode> optionalNode() {
        return ofNullable(json());
    }

    default <R extends JsonNode> Optional<R> to(Class<R> clazz) {
        return optionalNode().filter(node -> node.getClass().isAssignableFrom(clazz)).map(clazz::cast);
    }

    default Optional<ObjectNode> toObjectNode() {
        return to(ObjectNode.class);
    }

    default ObjectNode object() {
        return toObjectNode().orElseThrow(() -> new ClassCastException("Excepted type is " + ObjectNode.class + ", but was " + json().getClass().getName()));
    }

    default Optional<ArrayNode> toArrayNode() {
        return to(ArrayNode.class);
    }

    default ArrayNode array() {
        return toArrayNode().orElseThrow(() -> new ClassCastException("Excepted type is " + ObjectNode.class + ", but was " + json().getClass().getName()));
    }

    default boolean has(String name) {
        return optionalNode().map(n -> n.has(name)).orElse(false);
    }

    default Optional<JsonElement> get(String name) {
        return optionalNode().filter(node -> node.has(name)).map(node -> node.get(name)).map(child -> of(json()));
    }

    default JsonElement with(String name) {
        Objects.requireNonNull(name);
        boolean hasNestedElements = name.contains(".");
        List<String> elements = new ArrayList<>();
        if (hasNestedElements) {
            elements.addAll(List.of(name.split("\\.")));
        }
        String childNodeName = hasNestedElements ? elements.remove(0) : name;
        Optional<JsonNode> childNode = optionalNode().map(node -> node.get(childNodeName));

        if (strategy().equals(JsonElementStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST)) {
            JsonElement jsonElement = JsonElement.ofEphemeral(childNode.orElse(JsonNodeFactory.instance.objectNode()));
            // recurse for nested child nodes
            if (hasNestedElements) {
                return jsonElement.with(String.join(".", elements));
            }
            return jsonElement;
        }

        JsonElement jsonElement = JsonElement.of(childNode
                .orElseThrow(() -> new IllegalArgumentException("Node for '" + name + "' NOT found!\n" +
                        optionalNode()
                                .map(JsonNode::toString)
                                .orElse(null)))
        );

        // recurse for nested child nodes
        if (hasNestedElements) {
            return jsonElement.with(String.join(".", elements));
        }

        return jsonElement;
    }

    default JsonElement at(int index) {
        Optional<JsonNode> childNode = optionalNode().map(node -> node.get(index));

        if (strategy().equals(JsonElementStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST)) {
            return JsonElement.ofEphemeral(childNode.orElse(JsonNodeFactory.instance.arrayNode()));
        }

        return JsonElement.of(childNode
                .orElseThrow(() -> new IllegalArgumentException("Node at index '" + index + "' NOT found!\n" +
                        optionalNode()
                                .map(JsonNode::toString)
                                .orElse(null)))
        );
    }

    default Optional<String> asString() {
        return optionalNode().filter(JsonNode::isTextual).map(JsonNode::textValue);
    }

    default String asString(String defaultValue) {
        return asString().orElse(defaultValue);
    }

    default Optional<Integer> asInt() {
        return optionalNode().flatMap(value -> {
            if (value instanceof NumericNode number) {
                return Optional.of(number.intValue());
            }
            try {
                return Optional.of(Integer.parseInt(value.asText()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    default Integer asInt(Integer defaultValue) {
        return asInt().orElse(defaultValue);
    }

    default Optional<Long> asLong() {
        return optionalNode().flatMap(value -> {
            if (value instanceof NumericNode number) {
                return Optional.of(number.longValue());
            }
            try {
                return Optional.of(Long.parseLong(value.asText()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    default Long asLong(Long defaultValue) {
        return asLong().orElse(defaultValue);
    }

    default Optional<Float> asFloat() {
        return optionalNode().flatMap(value -> {
            if (value instanceof NumericNode number) {
                return Optional.of(number.floatValue());
            }
            try {
                return Optional.of(Float.parseFloat(value.asText()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    default Float asFloat(Float defaultValue) {
        return asFloat().orElse(defaultValue);
    }

    default Optional<Double> asDouble() {
        return optionalNode().flatMap(value -> {
            if (value instanceof NumericNode number) {
                return Optional.of(number.doubleValue());
            }
            try {
                return Optional.of(Double.parseDouble(value.asText()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    default Double asDouble(Double defaultValue) {
        return asDouble().orElse(defaultValue);
    }

    default Optional<Boolean> asBoolean() {
        return optionalNode().flatMap(value -> {
            if (value instanceof BooleanNode number) {
                return Optional.of(number.booleanValue());
            }
            try {
                return Optional.of(Boolean.parseBoolean(value.asText()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    default Boolean asBoolean(Boolean defaultValue) {
        return asBoolean().orElse(defaultValue);
    }

    @SuppressWarnings("unchecked")
    default <R, U extends JsonNode> List<R> toList(Function<U, R> mapFunction) {
        List<R> result = new ArrayList<>(optionalNode().map(JsonNode::size).orElse(0));
        toArrayNode().ifPresent(node -> {
            for (JsonNode child : node) {
                result.add(mapFunction.apply((U) child));
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    default <R, U extends JsonNode> Map<String, R> toMap(Function<U, R> mapFunction) {
        Map<String, R> result = new LinkedHashMap<>(optionalNode().map(JsonNode::size).orElse(0));
        toObjectNode().ifPresent(node -> {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                result.put(entry.getKey(), mapFunction.apply((U) entry.getValue()));
            }
        });
        return result;
    }

    static JsonElement of(JsonNode json) {
        return new JsonElementImpl(json);
    }

    static JsonElement ofEphemeral(JsonNode json) {
        return new JsonElementImpl(json, JsonElementStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST);
    }
}
