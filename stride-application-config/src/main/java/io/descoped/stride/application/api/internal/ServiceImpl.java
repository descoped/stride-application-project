package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Metadata;
import io.descoped.stride.application.api.config.Service;
import io.descoped.stride.application.api.exception.ExceptionFunction;
import io.descoped.stride.application.api.jackson.JsonElement;

import static java.util.Optional.ofNullable;

public record ServiceImpl(String name, ObjectNode json) implements Service {

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
    @SuppressWarnings("Convert2MethodRef")
    public Class<?> clazz() {
        return ofNullable(className())
                .map(ExceptionFunction.call(() -> s -> Class.forName(s))) // deal with hard exception
                .orElse(null);
    }

    @Override
    public int runLevel() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("runLevel"))
                .map(JsonNode::asText)
                .map(Integer::valueOf)
                .orElse(-2); // RunLevel.RUNLEVEL_VAL_INITIAL
    }

    @Override
    public Metadata metadata() {
        return ofNullable(json)
                .map(node -> node.get("metadata"))
                .map(ObjectNode.class::cast)
                .<Metadata>map(MetadataImpl::new)
                .orElseGet(() -> {
                    Metadata.Builder builder = Metadata.builder();
                    new ServiceBuilder(name, json).metadata(builder).build();
                    return builder.build();
                });
    }

    // ------------------------------------------------------------------------------------------------------------

    public record ServiceBuilder(String name, ObjectNode builder) implements Service.Builder {

        public ServiceBuilder(String name) {
            this(name, JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Service.Builder enabled(boolean enabled) {
            builder.set("enabled", builder.textNode(Boolean.toString(enabled)));
            return this;
        }

        @Override
        public Service.Builder className(String serviceClassName) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("class", builder.textNode(serviceClassName));
            return this;
        }

        @Override
        public Service.Builder clazz(Class<?> serviceClass) {
            return className(serviceClass.getName());
        }

        @Override
        public Service.Builder runLevel(int runlevel) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("runLevel", builder.textNode(String.valueOf(runlevel)));
            return this;
        }

        @Override
        public Service.Builder metadata(Metadata.Builder metadataBuilder) {
            builder.set("metadata", metadataBuilder.build().json());
            return this;
        }

        @Override
        public Service build() {
            return new ServiceImpl(name, builder);
        }
    }
}
