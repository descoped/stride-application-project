package io.descoped.stride.application.api.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.jackson.internal.JsonElementImpl;

import java.util.ArrayList;
import java.util.HashMap;
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

    JsonCreationStrategy strategy();

    default Optional<JsonNode> optionalNode() {
        return ofNullable(json());
    }

    default boolean isEmpty() {
        return optionalNode().isEmpty();
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
        return optionalNode().filter(node -> node.has(name)).map(node -> node.get(name)).map(JsonElement::ofStrict);
    }

    default JsonElement find(String name) {
        Objects.requireNonNull(name);
        List<String> elements = new ArrayList<>(List.of(name.split("\\.")));

        if (elements.isEmpty()) {
            return JsonElement.ofStrict(null);
        }

        JsonNode node = optionalNode().map(e -> e.findValue(elements.remove(0))).orElse(null);
        for (String element : elements) {
            if (node == null || !node.has(element)) {
                break;
            }
            node = node.get(element);
        }

        return switch (strategy()) {
            case STRICT -> JsonElement.ofStrict(node);
            case CREATE_NODE_IF_NOT_EXIST -> JsonElement.ofDynamic(node);
            case CREATE_EPHEMERAL_NODE_IF_NOT_EXIST -> JsonElement.ofEphemeral(node);
        };
    }

    default JsonElement with(String name) {
        Objects.requireNonNull(name);

        boolean hasNestedElements = name.contains(".") && !optionalNode().map(node -> node.has(name)).orElse(false);
        List<String> elements = new ArrayList<>();
        if (hasNestedElements) {
            elements.addAll(List.of(name.split("\\.")));
        }

        String childNodeName = hasNestedElements ? elements.remove(0) : name;
        Optional<JsonNode> childNode = optionalNode().map(node -> node.get(childNodeName));

        return switch (strategy()) {
            case STRICT -> {
                JsonElement jsonElement = JsonElement.ofStrict(childNode.orElse(null));
                // recurse nested child nodes
                if (hasNestedElements) {
                    yield jsonElement.with(String.join(".", elements));
                }
                yield jsonElement;
            }

            case CREATE_NODE_IF_NOT_EXIST -> {
                JsonElement jsonElement = JsonElement.ofDynamic(childNode.orElseGet(() -> {
                    // create new child node if empty
                    ObjectNode newNode = JsonNodeFactory.instance.objectNode();
                    object().set(childNodeName, newNode);
                    return newNode;
                }));
                // recurse nested child nodes
                if (hasNestedElements) {
                    yield jsonElement.with(String.join(".", elements));
                }
                yield jsonElement;
            }

            case CREATE_EPHEMERAL_NODE_IF_NOT_EXIST -> {
                JsonElement jsonElement = JsonElement.ofEphemeral(childNode.orElse(JsonNodeFactory.instance.objectNode()));
                // recurse nested child nodes
                if (hasNestedElements) {
                    yield jsonElement.with(String.join(".", elements));
                }
                yield jsonElement;
            }
        };
    }

    default JsonElement at(int index) {
        Optional<JsonNode> childNode = optionalNode().map(node -> node.get(index));

        return switch (strategy()) {
            case STRICT -> JsonElement.ofStrict(childNode.orElse(null));
            case CREATE_NODE_IF_NOT_EXIST ->
                    throw new UnsupportedOperationException("Dynamic node creation for at() is yet not supported!");
            case CREATE_EPHEMERAL_NODE_IF_NOT_EXIST ->
                    JsonElement.ofEphemeral(childNode.orElse(JsonNodeFactory.instance.arrayNode()));
        };
    }

    default Optional<String> asString() {
        return optionalNode().filter(JsonNode::isTextual).map(JsonNode::textValue);
    }

    default String asString(String defaultValue) {
        return asString().orElse(defaultValue);
    }

    default String asString(String with, String defaultValue) {
        return with(with).asString(defaultValue);
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

    default Integer asInt(String with, Integer defaultValue) {
        return with(with).asInt(defaultValue);
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

    default Long asLong(String with, Long defaultValue) {
        return with(with).asLong(defaultValue);
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

    default Float asFloat(String with, Float defaultValue) {
        return with(with).asFloat(defaultValue);
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

    default Double asDouble(String with, Double defaultValue) {
        return with(with).asDouble(defaultValue);
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

    default boolean asBoolean(Boolean defaultValue) {
        return Boolean.TRUE.equals(asBoolean().orElse(defaultValue));
    }

    default boolean asBoolean(String with, Boolean defaultValue) {
        return Boolean.TRUE.equals(with(with).asBoolean(defaultValue));
    }

    default <T> Optional<T> getObjectAs(Function<ObjectNode, T> mapper) {
        return optionalNode().map(ObjectNode.class::cast).map(mapper);
    }

    static Map<String, JsonNode> asMap(ObjectNode objectNode) {
        Map<String, JsonNode> map = new HashMap<>(objectNode.size());
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    static <T> void toFlattenedMap(Map<String, T> result, String prefix, ObjectNode object, Function<JsonNode, T> mapper) {
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> next = fields.next();
            if (next.getValue().getNodeType().equals(JsonNodeType.OBJECT)) {
                toFlattenedMap(result, prefix + next.getKey() + ".", (ObjectNode) next.getValue(), mapper);
            } else {
                JsonNode value = next.getValue();
                T mappedValue = mapper.apply(value);
                result.put(prefix + next.getKey(), mappedValue);
            }
        }
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

    static JsonElement ofStrict(JsonNode json) {
        return new JsonElementImpl(json);
    }

    static JsonElement ofDynamic(JsonNode json) {
        return new JsonElementImpl(json, JsonCreationStrategy.CREATE_NODE_IF_NOT_EXIST);
    }

    static JsonElement ofEphemeral(JsonNode json) {
        return new JsonElementImpl(json, JsonCreationStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST);
    }
}
