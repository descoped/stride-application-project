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
import java.util.Set;

import static java.util.Optional.ofNullable;

public record Filter(String name, ObjectNode json) {

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public boolean isEnabled() {
        return ofNullable(json)
                .map(node -> node.get("enabled"))
                .map(JsonNode::asText)
                .map(Boolean::parseBoolean)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }

    public String className() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("class"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    public <R extends jakarta.servlet.Filter> Class<R> clazz() {
        return ofNullable(className())
                .map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s))) // deal with hard exception
                .orElse(null);
    }

    public String pathSpec() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("pathSpec"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    public EnumSet<DispatcherType> dispatches() {
        List<DispatcherType> dispatches = JsonElement.ofStrict(json)
                .with("config.dispatches")
                .toList(node -> DispatcherType.valueOf(node.asText()));
        return EnumSet.copyOf(Set.copyOf(dispatches));
    }

    public ServletContext context() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("context"))
                .map(ObjectNode.class::cast)
                .map(ServletContext::new)
                .orElse(null);
    }

    // ------------------------------------------------------------------------------------------------------------

    public record Builder(String name, ObjectNode builder) {
        public Builder(String name) {
            this(name, JsonNodeFactory.instance.objectNode());
        }

        public Builder enabled(boolean enabled) {
            builder.set("enabled", builder.textNode(Boolean.toString(enabled)));
            return this;
        }

        public Builder className(String filterClassName) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("class", builder.textNode(filterClassName));
            return this;
        }

        public Builder clazz(Class<? extends jakarta.servlet.Filter> filterClass) {
            return className(filterClass.getName());
        }

        public Builder pathSpec(String pathSpec) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("pathSpec", builder.textNode(pathSpec));
            return this;
        }

        public Builder dispatches(EnumSet<DispatcherType> dispatches) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            dispatches.forEach(e -> arrayNode.add(builder.textNode(e.name())));
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("dispatches", arrayNode);
            return this;
        }

        public Builder context(ServletContext.Builder contextBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("context", contextBuilder.build().json());
            return this;
        }

        public Filter build() {
            return new Filter(name, builder);
        }
    }
}
