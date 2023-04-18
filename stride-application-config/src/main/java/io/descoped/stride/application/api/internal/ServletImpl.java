package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Servlet;
import io.descoped.stride.application.api.exception.ExceptionFunction;
import io.descoped.stride.application.api.jackson.JsonElement;

import static java.util.Optional.ofNullable;

public record ServletImpl(String name, ObjectNode json) implements Servlet {

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
    public <R extends jakarta.servlet.Servlet> Class<R> clazz() {
        return ofNullable(className())
                .map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s))) // deal with hard exception
                .orElse(null);
    }

    @Override
    public String pathSpec() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("pathSpec"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    @Override
    public ServletContextBinding binding() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("binding"))
                .map(ObjectNode.class::cast)
                .map(ServletContextBinding::new)
                .orElse(null);
    }

    @Override
    public ServletContextValidation validation() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("validation"))
                .map(ObjectNode.class::cast)
                .map(ServletContextValidation::new)
                .orElse(null);
    }

    // ------------------------------------------------------------------------------------------------------------

    public record ServletBuilder(String name, ObjectNode builder) implements Servlet.Builder {
        public ServletBuilder(String name) {
            this(name, JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Servlet.Builder enabled(boolean enabled) {
            builder.set("enabled", builder.textNode(Boolean.toString(enabled)));
            return this;
        }

        @Override
        public Servlet.Builder className(String servletClassName) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("class", builder.textNode(servletClassName));
            return this;
        }

        @Override
        public Servlet.Builder clazz(Class<? extends jakarta.servlet.Servlet> servletClass) {
            return className(servletClass.getName());
        }

        @Override
        public Servlet.Builder pathSpec(String pathSpec) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("pathSpec", builder.textNode(pathSpec));
            return this;
        }

        @Override
        public Servlet.Builder validate(ServletContextValidation.Builder servletContextValidationBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("validation", servletContextValidationBuilder.build().json());
            return this;
        }

        @Override
        public Servlet.Builder binding(ServletContextBinding.Builder ServletContextBindingBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("binding", ServletContextBindingBuilder.build().json());
            return this;
        }

        @Override
        public Servlet build() {
            return new ServletImpl(name, builder);
        }
    }
}
