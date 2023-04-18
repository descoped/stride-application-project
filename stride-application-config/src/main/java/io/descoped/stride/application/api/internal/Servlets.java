package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.ApplicationJson;
import io.descoped.stride.application.api.jackson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Servlets(ObjectNode json) {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get servlet by name
     *
     * @param name servlet name
     * @return Servlet config
     */
    public Optional<Servlet> servlet(String name) {
        return JsonElement.ofEphemeral(json)
                .with(name)
                .optionalNode()
                .map(ObjectNode.class::cast)
                .map(json -> new Servlet(name, json));
    }


    /**
     * Get servlet by class
     *
     * @param className servlet class
     * @return Servlet config
     */
    public Optional<Servlet> servletByClass(String className) {
        for (Servlet servlet : iterator()) {
            if (className.equals(servlet.className())) {
                return Optional.of(servlet);
            }
        }
        return Optional.empty();
    }

    public Iterable<Servlet> iterator() {
        List<Servlet> servlets = new ArrayList<>();
        Set<String> keys = ApplicationJson.keys(json); // resolve keySet for (this) servlets element
        for (String key : keys) {
            JsonElement.ofStrict(json)
                    .with(key)
                    .optionalNode()
                    .map(ObjectNode.class::cast)
                    .map(json -> new Servlet(key, json))
                    .map(servlets::add);
        }
        return servlets;
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Builder(ObjectNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder servlet(Servlet.Builder servletBuilder) {
            Servlet servlet = servletBuilder.build();
            builder.set(servlet.name(), servlet.json());
            return this;
        }

        public Servlets build() {
            return new Servlets(builder);
        }
    }
}
