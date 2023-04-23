package io.descoped.stride.application.config.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.api.ServletContextInitialization;
import io.descoped.stride.application.config.api.ServletContextValidation;
import io.descoped.stride.application.exception.api.ExceptionFunction;
import io.descoped.stride.application.jackson.api.JsonElement;

import java.util.List;
import java.util.stream.Collectors;

public record ServletContextInitializationImpl(ObjectNode json) implements ServletContextInitialization {

    @Override
    public List<Class<?>> classes() {
        return JsonElement.ofStrict(json)
                .with("config.classes")
                .toList(JsonNode::asText)
                .stream()
                .map(ExceptionFunction.call(() -> classname -> (Class<?>) Class.forName(classname)))
                .collect(Collectors.toList());
    }

    @Override
    public ServletContextValidationImpl validation() {
        return JsonElement.ofStrict(json)
                .with("conifg.validation")
                .toObjectNode()
                .map(ServletContextValidationImpl::new)
                .orElse(null);
    }

    public record ServletContextInitializationBuilder(ObjectNode builder)
            implements ServletContextInitialization.Builder {

        public ServletContextInitializationBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public <R> ServletContextInitialization.Builder initializerClass(Class<R> initializerClass) {
            ArrayNode initializerClassArrayNode = BuilderHelper.createOrGet(JsonElement.ofDynamic(builder).with("config").object(), "classes");
            initializerClassArrayNode.add(builder.textNode(initializerClass.getName()));
            return this;
        }

        @Override
        public ServletContextInitialization.Builder validate(ServletContextValidation.Builder servletContextValidationBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("validation", servletContextValidationBuilder.build().json());
            return this;
        }

        @Override
        public ServletContextInitialization build() {
            return new ServletContextInitializationImpl(builder);
        }
    }

}
