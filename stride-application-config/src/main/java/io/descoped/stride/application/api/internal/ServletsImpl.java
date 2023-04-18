package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Servlet;
import io.descoped.stride.application.api.config.Servlets;
import io.descoped.stride.application.api.jackson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ServletsImpl(ObjectNode json) implements Servlets {

    /**
     * Get servlet by name
     *
     * @param name servlet name
     * @return Servlet config
     */
    @Override
    public Optional<Servlet> servlet(String name) {
        return JsonElement.ofEphemeral(json)
                .with(name)
                .optionalNode()
                .map(ObjectNode.class::cast)
                .map(json -> new ServletImpl(name, json));
    }


    /**
     * Get servlet by class
     *
     * @param className servlet class
     * @return Servlet config
     */
    @Override
    public Optional<Servlet> servletByClass(String className) {
        for (Servlet servlet : iterator()) {
            if (className.equals(servlet.className())) {
                return Optional.of(servlet);
            }
        }
        return Optional.empty();
    }

    @Override
    public Iterable<Servlet> iterator() {
        List<Servlet> servlets = new ArrayList<>();
        Set<String> keys = ApplicationJson.keys(json); // resolve keySet for (this) servlets element
        for (String key : keys) {
            JsonElement.ofStrict(json)
                    .with(key)
                    .optionalNode()
                    .map(ObjectNode.class::cast)
                    .map(json -> new ServletImpl(key, json))
                    .map(servlets::add);
        }
        return servlets;
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record ServletsBuilder(ObjectNode builder) implements Servlets.Builder {
        public ServletsBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Servlets.Builder servlet(Servlet.Builder servletBuilder) {
            Servlet servlet = servletBuilder.build();
            builder.set(servlet.name(), servlet.json());
            return this;
        }

        @Override
        public Servlets build() {
            return new ServletsImpl(builder);
        }
    }
}
