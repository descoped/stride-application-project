package io.descoped.stride.application.config.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns flat properties into a JSON tree. A key is split on {@code '.'}; a node whose children are all
 * non-negative integers ({@code dispatches.0}, {@code dispatches.1}) becomes an array ordered by index, any other
 * node an object. Every value is text. An array element with an empty value is left out.
 */
final class PropertyTree {

    private final Map<String, PropertyTree> children = new LinkedHashMap<>();
    private String value;

    private PropertyTree() {
    }

    static ObjectNode toJson(Map<String, String> properties) {
        PropertyTree root = new PropertyTree();
        properties.forEach((key, value) -> {
            PropertyTree node = root;
            for (String segment : key.split("\\.")) {
                if (node.value != null) {
                    throw conflict(key);
                }
                node = node.children.computeIfAbsent(segment, k -> new PropertyTree());
            }
            if (!node.children.isEmpty()) {
                throw conflict(key);
            }
            node.value = value;
        });
        return root.toObject();
    }

    private static IllegalArgumentException conflict(String key) {
        return new IllegalArgumentException("Property '" + key + "' is both a value and a parent of other properties");
    }

    private JsonNode toNode() {
        if (children.isEmpty()) {
            return JsonNodeFactory.instance.textNode(value);
        }
        return isArray() ? toArray() : toObject();
    }

    private ObjectNode toObject() {
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        children.forEach((name, child) -> object.set(name, child.toNode()));
        return object;
    }

    private ArrayNode toArray() {
        Map<Integer, PropertyTree> byIndex = new TreeMap<>();
        children.forEach((index, child) -> byIndex.put(Integer.valueOf(index), child));
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (PropertyTree element : byIndex.values()) {
            if (element.children.isEmpty() && element.value.isEmpty()) {
                continue;
            }
            array.add(element.toNode());
        }
        return array;
    }

    private boolean isArray() {
        return children.keySet().stream().allMatch(PropertyTree::isIndex);
    }

    private static boolean isIndex(String segment) {
        if (segment.isEmpty() || segment.length() > 9) {
            return false;
        }
        return segment.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
