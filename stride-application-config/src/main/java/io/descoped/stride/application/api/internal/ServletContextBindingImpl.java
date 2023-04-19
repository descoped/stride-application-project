package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.ServletContextBinding;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Optional.ofNullable;

public record ServletContextBindingImpl(ObjectNode json) implements ServletContextBinding {

    @Override
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Override
    public String serviceRef(String name) {
        return ofNullable(json)
                .map(node -> node.get(name))
                .map(JsonNode::asText)
                .orElse(null);
    }

    public record ServletContextBindingBuilder(ObjectNode builder) implements ServletContextBinding.Builder {
        public ServletContextBindingBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public ServletContextBinding.Builder bind(String name, String named) {
            builder.set(name, builder.textNode(named));
            return this;
        }

        @Override
        public ServletContextBinding.Builder bind(String name, Class<?> named) {
            return bind(name, named.getName());
        }

        @Override
        public ServletContextBinding build() {
            return new ServletContextBindingImpl(builder);
        }
    }
}
