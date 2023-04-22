package io.descoped.stride.application.api.config.internal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Filter;
import io.descoped.stride.application.api.config.ServletContextBinding;
import io.descoped.stride.application.api.config.ServletContextValidation;
import io.descoped.stride.application.api.jackson.JsonElement;
import jakarta.servlet.DispatcherType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record FilterImpl(String name, ObjectNode json) implements Filter {

    @Override
    public boolean isEnabled() {
        return JsonElement.ofEphemeral(json)
                .with("enabled")
                .asBoolean(false);
    }

    @Override
    public String className() {
        return JsonElement.ofEphemeral(json)
                .with("config.class")
                .asString(null);
    }

    @Override
    public <R extends jakarta.servlet.Filter> Class<R> clazz() {
        return JsonElement.ofEphemeral(json)
                .with("config.class")
                .asClass();
    }

    @Override
    public String pathSpec() {
        return JsonElement.ofEphemeral(json)
                .with("config.pathSpec")
                .asString(null);
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
        return JsonElement.ofEphemeral(json)
                .with("config.context")
                .toObjectNode()
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
