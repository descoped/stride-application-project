package io.descoped.stride.application.config.internal;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ApplicationConfigurationJson {

    private final JsonNode json;

    public ApplicationConfigurationJson(String properties) {
        this(propertiesToMap(properties));
    }

    public ApplicationConfigurationJson(Map<String, String> properties) {
        this.json = PropertyTree.toJson(properties);
    }

    public ApplicationConfigurationJson(JsonNode json) {
        this.json = json;
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
        return props.entrySet().stream().collect(
                Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue()),
                        (prev, next) -> next, LinkedHashMap::new
                ));
    }

    static final List<String> fieldMatcher = List.of("enabled", "config", "metadata");
    static final Predicate<String> edgeFieldPredicate = fieldMatcher::contains;
    static final Function<Set<Node>, String> formatAncestors = ancestors ->
            ancestors.stream().skip(1).map(Node::fieldName).collect(Collectors.joining("."));
    static final Function<Set<Node>, String> indentAncestors = ancestors ->
            Arrays.stream(new String[ancestors.size()]).map(element -> " ").collect(Collectors.joining());

    Set<String> keys(String fieldName) {
        return keys(json.get(fieldName));
    }

    static Set<String> keys(JsonNode fromNode) {
        Set<String> keys = new LinkedHashSet<>();
        depthFirstPreOrderFullTraversal(Node.root(fromNode), new LinkedHashSet<>(), new LinkedHashSet<>(), (ancestors, node) -> {
            if (ancestors.isEmpty()) {
                return false;
            }
            if (edgeFieldPredicate.test(node.fieldName)) {
                String key = formatAncestors.apply(ancestors);
                if (!key.isBlank()) {
                    keys.add(key);
                }
                return true;
            }
            return false;
        });
        return keys;
    }

    private static void depthFirstPreOrderFullTraversal(Node current,
                                                        Set<String> visited,
                                                        Set<Node> ancestors,
                                                        BiPredicate<Set<Node>, Node> visit) {

        String currentPath = formatAncestors.apply(ancestors) + "." + current.fieldName;
        if (!visited.add(currentPath)) {
            return;
        }

        if (visit.test(ancestors, current)) {
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
                depthFirstPreOrderFullTraversal(child, visited, ancestors, visit);
            }
        } finally {
            ancestors.remove(current);
        }
    }

    private record Node(String fieldName, JsonNode node) {
        static Node root(JsonNode node) {
            return new Node(null, node);
        }

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
