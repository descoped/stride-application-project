package io.descoped.stride.application.api.config.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Arg;
import io.descoped.stride.application.api.config.Args;
import io.descoped.stride.application.api.config.Resource;
import io.descoped.stride.application.api.exception.ExceptionFunction;
import io.descoped.stride.application.api.jackson.JsonElement;

import java.util.Collections;
import java.util.List;

import static java.util.Optional.ofNullable;

public record ResourceImpl(String name, ObjectNode json) implements Resource {

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
    public <R> Class<R> clazz() {
        return ofNullable(className())
                .map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s))) // deal with hard exception
                .orElse(null);
    }

    @Override
    public List<Arg> args() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("args"))
                .map(ArrayNode.class::cast)
                .map(ArgsImpl::new)
                .map(Args::args)
                .orElse(Collections.emptyList());
    }

    // ------------------------------------------------------------------------------------------------------------

    public record ResourceBuilder(String name, ObjectNode builder) implements Resource.Builder {
        public ResourceBuilder(String name) {
            this(name, JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Resource.Builder enabled(boolean enabled) {
            builder.set("enabled", builder.textNode(Boolean.toString(enabled)));
            return this;
        }

        @Override
        public Resource.Builder className(String resourceClassName) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("class", builder.textNode(resourceClassName));
            return this;
        }

        @Override
        public <R> Resource.Builder clazz(Class<R> resourceClass) {
            return className(resourceClass.getName());
        }

        @Override
        public Resource.Builder args(ArgsImpl.ArgsBuilder argsBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("args", argsBuilder.build().json());
            return this;
        }

        @Override
        public Resource build() {
            return new ResourceImpl(name, builder);
        }
    }
}
