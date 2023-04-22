package io.descoped.stride.application.api.config.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Arg;
import io.descoped.stride.application.api.config.Args;
import io.descoped.stride.application.api.config.Resource;
import io.descoped.stride.application.api.jackson.JsonElement;

import java.util.Collections;
import java.util.List;

public record ResourceImpl(String name, ObjectNode json) implements Resource {

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
    public <R> Class<R> clazz() {
        return JsonElement.ofEphemeral(json)
                .with("config.class")
                .asClass();
    }

    @Override
    public List<Arg> args() {
        return JsonElement.ofEphemeral(json)
                .with("config.args")
                .toArrayNode()
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
        public Resource.Builder args(Args.Builder argsBuilder) {
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
