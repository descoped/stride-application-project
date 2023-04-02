package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import no.cantara.config.ApplicationProperties;
import no.cantara.config.json.PropertyMapToJsonConverter;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public final class ApplicationJson {

    private final ApplicationProperties properties;
    private final JsonNode json;

    public ApplicationJson(String properties) {
        this(mapToApplicationProperties(propertiesToMap(properties)));
    }

    public ApplicationJson(ApplicationProperties properties) {
        this.properties = properties;
        PropertyMapToJsonConverter converter = new PropertyMapToJsonConverter(properties.map());
        json = converter.json();
    }

    public ApplicationJson(JsonNode json) {
        this.properties = null;
        this.json = json;
    }

    public ApplicationProperties properties() {
        return properties;
    }

    public JsonNode json() {
        return json;
    }

    static Map<String, String> propertiesToMap(String properties) {
        Properties props = new Properties();
        try {
            props.load(new StringReader(properties));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String, String> map = props.entrySet().stream().collect(
                Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue()),
                        (prev, next) -> next, LinkedHashMap::new
                ));
        return map;
    }

    static ApplicationProperties mapToApplicationProperties(Map<String, String> map) {
        return ApplicationProperties.builder()
                .map(map)
                .build();
    }

    public Set<String> keys(String fieldName) {
        Set<String> keys = new LinkedHashSet<>();
        depthFirstPreOrderFullTraversal(0, Node.of(null, json.get(fieldName)), new LinkedHashSet<>(), new LinkedList<>(), (ancestors, node) -> {
            if (ancestors.size() == 0) return false;
            //String indent = Arrays.stream(new String[ancestors.size()]).map(element -> " ").collect(Collectors.joining());
            boolean match = List.of("enabled", "config", "metadata").contains(node.fieldName);
            if (match) {
                String key = ancestors.stream().skip(1).map(Node::fieldName).collect(Collectors.joining("."));
                keys.add(key);
            }
            return match;
        });
        return keys;
    }

    private void depthFirstPreOrderFullTraversal(int depth, Node current, Set<Node> visited, List<Node> ancestors, BiFunction<List<Node>, Node, Boolean> visit) {
        if (!visited.add(current)) {
            return;
        }

        if (visit.apply(ancestors, current)) {
            return;
        }

        ancestors.add(current);
        try {
            if (current.node == null) {
                return;
            }
            for (Iterator<String> it = current.node.fieldNames(); it.hasNext(); ) {
                String fieldName = it.next();
                Node child = Node.of(fieldName, current.node.get(fieldName));
                depthFirstPreOrderFullTraversal(depth + 1, child, visited, ancestors, visit);
            }
        } finally {
            ancestors.remove(current);
        }
    }

    private record Node(String fieldName, JsonNode node) {
        static Node of(String fieldName, JsonNode node) {
            return new Node(fieldName, node);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node node1 = (Node) o;
            return node.equals(node1.node);
        }

        @Override
        public int hashCode() {
            return Objects.hash(node);
        }
    }
}
