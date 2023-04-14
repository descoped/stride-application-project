package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.exception.ExceptionFunction;
import io.descoped.stride.application.jackson.JsonElement;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ServletContextInitialization(ObjectNode json) {

    public static Builder builder() {
        return new Builder();
    }

    public List<Class<ServletContextInitializer>> initializers() {
        return JsonElement.ofStrict(json)
                .with("initializers")
                .toList(JsonNode::asText)
                .stream()
                .map(ExceptionFunction.call(() -> classname -> (Class<ServletContextInitializer>) Class.forName(classname)))
                .collect(Collectors.toList());
    }

    public record Builder(ObjectNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public <R extends ServletContextInitializer> Builder initializer(Class<R> initializerClass) {
            return initializer(initializerClass, ServletContextInitialization.produces());
        }

        public <R extends ServletContextInitializer> Builder initializer(Class<R> initializerClass, Produces.Builder producesBuilder) {
            ArrayNode initializerClassArrayNode;
            if (builder.has("initializers")) {
                initializerClassArrayNode = (ArrayNode) builder.get("initializers");
            } else {
                initializerClassArrayNode = JsonNodeFactory.instance.arrayNode();
                builder.set("initializers", initializerClassArrayNode);
            }
            initializerClassArrayNode.add(builder.textNode(initializerClass.getName()));
            return this;
        }

        public ServletContextInitialization build() {
            return new ServletContextInitialization(builder);
        }
    }

    // --------------------------------------------------------------------------------------------------------------

    public static Produces.Builder produces() {
        return new Produces.Builder();
    }

    public record Produces(ObjectNode json) {

        public record Builder(ObjectNode builder) {
            public Builder() {
                this(JsonNodeFactory.instance.objectNode());
            }

            public Builder produce(Set<String> namedSet) {

                return this;
            }

            public Produces build() {
                return new Produces(builder);
            }
        }
    }
}
