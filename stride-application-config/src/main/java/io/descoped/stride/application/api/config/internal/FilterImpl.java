package io.descoped.stride.application.api.config.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Filter;
import io.descoped.stride.application.api.config.ServletContextBinding;
import io.descoped.stride.application.api.config.ServletContextValidation;
import io.descoped.stride.application.api.exception.ExceptionFunction;
import io.descoped.stride.application.api.jackson.JsonElement;
import jakarta.servlet.DispatcherType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static java.util.Optional.ofNullable;

public record FilterImpl(String name, ObjectNode json) implements Filter {

    @Override
    public boolean isEnabled() {
        return ofNullable(json)
                .map(node -> node.get("enabled"))
                .map(JsonNode::asText)
                .map(Boolean::parseBoolean)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }

    @Override
    public String className() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("class"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R extends jakarta.servlet.Filter> Class<R> clazz() {
        return ofNullable(className())
                .map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s))) // deal with hard exception
                .orElse(null);
    }

    @Override
    public String pathSpec() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("pathSpec"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    @Override
    public EnumSet<DispatcherType> dispatches() {
        List<DispatcherType> dispatches = JsonElement.ofStrict(json)
                .with("config.dispatches")
                .toList(node -> DispatcherType.valueOf(node.asText()));
        return EnumSet.copyOf(Set.copyOf(dispatches));
    }

    @Override
    public ServletContextBinding context() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("context"))
                .map(ObjectNode.class::cast)
                .map(ServletContextBindingImpl::new)
                .orElse(null);
    }

    // ------------------------------------------------------------------------------------------------------------

    public record FilterBuilder(String name, ObjectNode builder) implements Filter.Builder {
        public FilterBuilder(String name) {
            this(name, JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Builder enabled(boolean enabled) {
            builder.set("enabled", builder.textNode(Boolean.toString(enabled)));
            return this;
        }

        @Override
        public Builder className(String filterClassName) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("class", builder.textNode(filterClassName));
            return this;
        }

        @Override
        public Builder clazz(Class<? extends jakarta.servlet.Filter> filterClass) {
            return className(filterClass.getName());
        }

        @Override
        public Builder pathSpec(String pathSpec) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("pathSpec", builder.textNode(pathSpec));
            return this;
        }

        @Override
        public Builder dispatches(EnumSet<DispatcherType> dispatches) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            dispatches.forEach(e -> arrayNode.add(builder.textNode(e.name())));
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("dispatches", arrayNode);
            return this;
        }

        @Override
        public Builder context(ServletContextValidation.Builder contextBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("context", contextBuilder.build().json());
            return this;
        }

        @Override
        public Filter build() {
            return new FilterImpl(name, builder);
        }
    }
}
