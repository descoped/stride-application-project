package io.descoped.stride.application.config.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.Metadata;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.jackson.JsonElement;

public record ServiceImpl(String name, ObjectNode json) implements Service {

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
    public Class<?> clazz() {
        return JsonElement.ofEphemeral(json)
                .with("config.class")
                .asClass();
    }

    @Override
    public int runLevel() {
        return JsonElement.ofEphemeral(json)
                .with("config.runLevel")
                .asInt(-2); // RunLevel.RUNLEVEL_VAL_INITIAL
    }

    @Override
    public Metadata metadata() {
        return JsonElement.ofEphemeral(json)
                .with("metadata")
                .toObjectNode()
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
