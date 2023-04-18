package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.exception.ExceptionFunction;
import io.descoped.stride.application.api.jackson.JsonElement;

import static java.util.Optional.ofNullable;

public record Service(String name, ObjectNode json) {

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static Builder builder(Class<?> clazz) {
        return new Builder(clazz.getName());
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

    @SuppressWarnings("Convert2MethodRef")
    public Class<?> clazz() {
        return ofNullable(className())
                .map(ExceptionFunction.call(() -> s -> Class.forName(s))) // deal with hard exception
                .orElse(null);
    }

    public int runLevel() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("runLevel"))
                .map(JsonNode::asText)
                .map(Integer::valueOf)
                .orElse(-2); // RunLevel.RUNLEVEL_VAL_INITIAL
    }

    public Metadata metadata() {
        return ofNullable(json)
                .map(node -> node.get("metadata"))
                .map(ObjectNode.class::cast)
                .map(Metadata::new)
                .orElseGet(() -> {
                    Metadata.Builder builder = Metadata.builder();
                    new Builder(name, json).metadata(builder).build();
                    return builder.build();
                });
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

        public Builder className(String serviceClassName) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("class", builder.textNode(serviceClassName));
            return this;
        }

        public Builder clazz(Class<?> serviceClass) {
            return className(serviceClass.getName());
        }

        public Builder runLevel(int runlevel) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("runLevel", builder.textNode(String.valueOf(runlevel)));
            return this;
        }

        public Builder metadata(Metadata.Builder metadataBuilder) {
            builder.set("metadata", metadataBuilder.build().json());
            return this;
        }

        public Service build() {
            return new Service(name, builder);
        }
    }
}
