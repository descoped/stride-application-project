package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.exception.ExceptionFunction;
import io.descoped.stride.application.jackson.JsonElement;
import jakarta.servlet.DispatcherType;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Optional.ofNullable;

public record Filters(ArrayNode json) {

    public static Filters.Builder builder() {
        return new Filters.Builder();
    }

    public static Filters.Filter.Builder filterBuilder() {
        return new Filters.Filter.Builder();
    }

    private Optional<JsonNode> findNode(String withProperty, String equalTo) {
        if (equalTo == null) {
            return Optional.empty();
        }
        for (JsonNode itemNode : json) {
            JsonElement itemElement = JsonElement.ofEphemeral(itemNode);
            if (equalTo.equals(itemElement.asString(withProperty, null))) {
                return itemElement.optionalNode();
            }
        }
        return Optional.empty();
    }

    /**
     * Lookup servlet by name
     *
     * @param name servletName
     * @return Servlet config
     */
    public Optional<Filters.Filter> filterName(String name) {
        return findNode("name", name).map(ObjectNode.class::cast).map(Filters.Filter::new);
    }

    /**
     * Lookup servlet by class
     *
     * @param className servlet class
     * @return Servlet config
     */
    public Optional<Filters.Filter> filterClass(String className) {
        return findNode("filterClass", className).map(ObjectNode.class::cast).map(Filters.Filter::new);
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Builder(ArrayNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.arrayNode());
        }

        public Builder filter(Filter.Builder filterBuilder) {
            builder.add(filterBuilder.build().json());
            return this;
        }

        public Filters build() {
            return new Filters(builder);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Filter(ObjectNode json) {

        public String name() {
            return ofNullable(json)
                    .map(node -> node.get("name"))
                    .map(JsonNode::asText)
                    .orElse(null);
        }

        public String className() {
            return ofNullable(json)
                    .map(node -> node.get("filterClass"))
                    .map(JsonNode::asText)
                    .orElse(null);
        }

        @SuppressWarnings("unchecked")
        public <R extends Filter> Class<R> clazz() {
            return ofNullable(className())
                    .map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s))) // deal with hard exception
                    .orElse(null);
        }

        public String pathSpec() {
            return ofNullable(json)
                    .map(node -> node.get("pathSpec"))
                    .map(JsonNode::asText)
                    .orElse(null);
        }

        public EnumSet<DispatcherType> dispatches() {
            List<DispatcherType> dispatches = JsonElement.of(json)
                    .with("dispatches")
                    .toList(node -> DispatcherType.valueOf(node.asText()));
            return EnumSet.copyOf(Set.copyOf(dispatches));
        }

        // ------------------------------------------------------------------------------------------------------------

        public record Builder(ObjectNode builder) {
            public Builder() {
                this(JsonNodeFactory.instance.objectNode());
            }

            public Builder name(String name) {
                builder.set("name", builder.textNode(name));
                return this;
            }

            public Builder className(String filterClassName) {
                builder.set("filterClass", builder.textNode(filterClassName));
                return this;
            }

            public Builder clazz(Class<? extends jakarta.servlet.Filter> filterClass) {
                return className(filterClass.getName());
            }

            public Builder pathSpec(String pathSpec) {
                builder.set("pathSpec", builder.textNode(pathSpec));
                return this;
            }

            public Builder dispatches(EnumSet<DispatcherType> dispatches) {
                ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
                dispatches.forEach(e -> arrayNode.add(builder.textNode(e.name())));
                builder.set("dispatches", arrayNode);
                return this;
            }

            public Filter build() {
                return new Filter(builder);
            }
        }
    }
}
