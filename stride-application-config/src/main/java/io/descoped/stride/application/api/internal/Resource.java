package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Args;
import io.descoped.stride.application.api.exception.ExceptionFunction;
import io.descoped.stride.application.api.jackson.JsonElement;

import java.util.Collections;
import java.util.List;

import static java.util.Optional.ofNullable;

public record Resource(String name, ObjectNode json) {
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
    public <R> Class<R> clazz() {
        return ofNullable(className())
                .map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s))) // deal with hard exception
                .orElse(null);
    }

    public List<ArgImpl> args() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("args"))
                .map(ArrayNode.class::cast)
                .map(ArgsImpl::new)
                .map(Args::args)
                .orElse(Collections.emptyList());
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

        public Builder className(String resourceClassName) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("class", builder.textNode(resourceClassName));
            return this;
        }

        public <R> Builder clazz(Class<R> resourceClass) {
            return className(resourceClass.getName());
        }

        public Builder args(ArgsImpl.ArgsBuilder argsBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("args", argsBuilder.build().json());
            return this;
        }

        public Resource build() {
            return new Resource(name, builder);
        }
    }
}
