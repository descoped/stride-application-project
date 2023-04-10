package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.exception.ExceptionFunction;
import io.descoped.stride.application.jackson.JsonElement;

import static java.util.Optional.ofNullable;

public record Servlet(String name, ObjectNode json) {
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
    public <R extends jakarta.servlet.Servlet> Class<R> clazz() {
        return ofNullable(className())
                .map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s))) // deal with hard exception
                .orElse(null);
    }

    public String pathSpec() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("pathSpec"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    public ServletContext context() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("context"))
                .map(ObjectNode.class::cast)
                .map(ServletContext::new)
                .orElse(null);
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

        public Builder className(String servletClassName) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("class", builder.textNode(servletClassName));
            return this;
        }

        public Builder clazz(Class<? extends jakarta.servlet.Servlet> servletClass) {
            return className(servletClass.getName());
        }

        public Builder pathSpec(String pathSpec) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("pathSpec", builder.textNode(pathSpec));
            return this;
        }

        public Builder context(ServletContext.Builder contextBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("context", contextBuilder.build().json());
            return this;
        }

        public Servlet build() {
            return new Servlet(name, builder);
        }
    }
}
