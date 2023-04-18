package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Optional.ofNullable;

public record ServletContextBinding(ObjectNode json) {

    public static Builder builder() {
        return new Builder();
    }

    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }

    public String serviceRef(String name) {
        return ofNullable(json)
                .map(node -> node.get(name))
                .map(JsonNode::asText)
                .orElse(null);
    }

    public record Builder(ObjectNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder bind(String name, String named) {
            builder.set(name, builder.textNode(named));
            return this;
        }

        public Builder bind(String name, Class<?> named) {
            return bind(name, named.getName());
        }

        public ServletContextBinding build() {
            return new ServletContextBinding(builder);
        }
    }
}
