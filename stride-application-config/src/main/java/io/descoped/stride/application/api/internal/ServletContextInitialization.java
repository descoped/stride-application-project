package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.exception.ExceptionFunction;
import io.descoped.stride.application.api.jackson.JsonElement;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public record ServletContextInitialization(ObjectNode json) {

    public static Builder builder() {
        return new Builder();
    }

    public List<Class<?>> initializers() {
        return JsonElement.ofStrict(json)
                .with("config")
                .with("classes")
                .toList(JsonNode::asText)
                .stream()
                .map(ExceptionFunction.call(() -> classname -> (Class<?>) Class.forName(classname)))
                .collect(Collectors.toList());
    }

    public ServletContextValidation validation() {
        return ofNullable(json)
                .map(node -> node.get("config"))
                .map(node -> node.get("validation"))
                .map(ObjectNode.class::cast)
                .map(ServletContextValidation::new)
                .orElse(null);
    }

    public record Builder(ObjectNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public <R> Builder initializer(Class<R> initializerClass) {
            ArrayNode initializerClassArrayNode = BuilderHelper.createOrGet(JsonElement.ofDynamic(builder).with("config").object(), "classes");
            initializerClassArrayNode.add(builder.textNode(initializerClass.getName()));
            return this;
        }

        public Builder validate(ServletContextValidation.Builder servletContextValidationBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("validation", servletContextValidationBuilder.build().json());
            return this;
        }

        public ServletContextInitialization build() {
            return new ServletContextInitialization(builder);
        }
    }

}
