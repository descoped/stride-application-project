package io.descoped.stride.application.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.ofNullable;

public record PropertyMapToJsonConverter(Map<String, String> properties, ObjectNode json) {

    public PropertyMapToJsonConverter(Map<String, String> propertyMap) {
        this(propertyMap, JsonNodeFactory.instance.objectNode());

        List<Property> propertyList = propertyMap.entrySet().stream()
                .map(e -> new PropertyTokenizer(e.getKey(), e.getValue()))
                .map(t -> t.property)
                .toList();

        Map<String, JsonNode> parentPathMap = new LinkedHashMap<>();
        parentPathMap.put("ROOT", json);

        Set<String> visitedPaths = new LinkedHashSet<>();

        for (int i = 0; i < propertyList.size(); i++) {
            Property property = propertyList.get(i);

            for (int j = 0; j < property.elements.size(); j++) {
                PropertyElement propertyElement = property.elements.get(j);

                //String pathElements = property.elements.stream().map(m -> m.key() + "[" + m.type() + "]").collect(Collectors.joining(".")) + "=" + property.value;
                String nextParentPathElements = getPathElementsByProperty(property, j + 1);
                String parentPathElements = getPathElementsByProperty(property, j);

                JsonNode parentNode = parentPathMap.get(parentPathElements);

                JsonNode jsonNode = switch (propertyElement.type()) {
                    case LEAF_NODE, ARRAY_ELEMENT -> JsonNodeFactory.instance.textNode(property.value);
                    case OBJECT, ARRAY_OBJECT -> JsonNodeFactory.instance.objectNode();
                    case ARRAY_NODE -> JsonNodeFactory.instance.arrayNode();
                };

                // skip already handled path
                if (!visitedPaths.add(nextParentPathElements)) {
                    continue;
                }

                // set next parentNode
                JsonNode childNode = parentPathMap.computeIfAbsent(nextParentPathElements, k -> jsonNode);

                if (parentNode instanceof ObjectNode parent) {
                    parent.set(propertyElement.key(), childNode);

                } else if (parentNode instanceof ArrayNode parent) {
                    parent.add(childNode);

                } else {
                    throw new IllegalStateException("property-element-pos: " + i + " => " + parentNode);
                }
            }
        }
    }

    private static String getPathElementsByProperty(Property property, int limit) {
        List<String> parentPathElementList = property.elements.stream().limit(limit).map(PropertyElement::key).toList();
        return "ROOT" + (parentPathElementList.isEmpty() ? "" : "." + String.join(".", parentPathElementList));
    }

    record PropertyTokenizer(String key, String value, Property property) {
        private static final Pattern INTEGER_PATTERN = Pattern.compile("^\\d+$");

        PropertyTokenizer(String property, String value) {
            this(property, value, tokenize(property, value));
        }

        static Property tokenize(String property, String value) {
            List<PropertyElement> elementList = new ArrayList<>();

            List<String> list = List.of(property.split("\\."));
            String previous = null;
            for (int i = 0; i < list.size(); i++) {
                String current = list.get(i);
                String next = (i + 1 < list.size()) ? list.get(i + 1) : null;

                if (isLeafNode(current, next)) {
                    elementList.add(PropertyElement.of(current, ElementType.LEAF_NODE));

                } else if (isArrayNode(previous, current) && isArrayObject(current, next)) {
                    elementList.add(PropertyElement.of(current, ElementType.ARRAY_OBJECT));

                } else if (isArrayNode(previous, current) && isArrayElement(current)) {
                    elementList.add(PropertyElement.of(current, ElementType.ARRAY_ELEMENT));

                } else if (isArrayNode(current, next)) {
                    elementList.add(PropertyElement.of(current, ElementType.ARRAY_NODE));

                } else if (isObject(current)) {
                    elementList.add(PropertyElement.of(current, ElementType.OBJECT));

                } else {
                    throw new IllegalStateException(String.format("Unknown type: [elementIndex: %s] %s <- %s", i, current, i > 0 ? list.get(i - 1) : "(null)"));
                }

                previous = current;
            }

            return new Property(property, value, elementList);
        }

        private static boolean isLeafNode(String token, String nextToken) {
            return isObject(token) && nextToken == null;
        }

        private static boolean isObject(String token) {
            return !isArrayElement(token);
        }

        private static boolean isArrayNode(String token, String nextToken) {
            return isObject(token) && isArrayElement(nextToken);
        }

        private static boolean isArrayElement(String token) {
            return ofNullable(token)
                    .map(INTEGER_PATTERN::matcher)
                    .map(Matcher::find)
                    .orElse(false);
        }

        private static boolean isArrayObject(String token, String nextToken) {
            return isArrayElement(token) && (nextToken != null && isObject(nextToken));
        }
    }

    enum ElementType {
        LEAF_NODE,
        OBJECT,
        ARRAY_NODE,
        ARRAY_ELEMENT,
        ARRAY_OBJECT;
    }


    record Property(String key, String value, List<PropertyElement> elements) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Property property = (Property) o;
            return key.equals(property.key) && Objects.equals(value, property.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }
    }

    interface PropertyElement {

        String key();

        ElementType type();

        record PropertyElementImpl(String key, ElementType type) implements PropertyElement {
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                PropertyElementImpl that = (PropertyElementImpl) o;
                return Objects.equals(key, that.key) && type == that.type;
            }

            @Override
            public int hashCode() {
                return Objects.hash(key, type);
            }
        }

        static PropertyElement of(String property, ElementType type) {
            return new PropertyElementImpl(property, type);
        }
    }
}
