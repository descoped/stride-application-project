package io.descoped.stride.application.config.api.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.api.ServletContextBinding;
import io.descoped.stride.application.jackson.api.JsonElement;

import java.util.LinkedHashSet;
import java.util.Set;

public record ServletContextBindingImpl(ObjectNode json) implements ServletContextBinding {

    @Override
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Override
    public String namedServiceByName(String name) {
        return JsonElement.ofEphemeral(json)
                .with(name)
                .asString(null);
    }

    public record ServletContextBindingBuilder(ObjectNode builder) implements ServletContextBinding.Builder {

        public ServletContextBindingBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public ServletContextBinding.Builder bind(String name, String named) {
            builder.set(name, builder.textNode(named));
            return this;
        }

        @Override
        public ServletContextBinding.Builder bind(String name, Class<?> named) {
            return bind(name, named.getName());
        }

        @Override
        public ServletContextBinding build() {
            return new ServletContextBindingImpl(builder);
        }
    }
}
