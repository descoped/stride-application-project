package io.descoped.stride.application.api.config.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Arg;
import io.descoped.stride.application.api.jackson.JsonElement;

public record ArgImpl(ObjectNode json) implements Arg {

    @Override
    public Class<?> clazz() {
        return JsonElement.ofEphemeral(json)
                .with("class")
                .asClass();
    }

    @Override
    public String named() {
        return JsonElement.ofEphemeral(json)
                .with("named")
                .asString(null);
    }

    public record ArgBuilder(ObjectNode builder) implements Arg.Builder {

        public ArgBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Builder arg(Class<?> clazz, String named) {
            builder.set("class", builder.textNode(clazz.getName()));
            builder.set("named", builder.textNode(named));
            return this;
        }

        @Override
        public Arg build() {
            return new ArgImpl(builder);
        }
    }
}
