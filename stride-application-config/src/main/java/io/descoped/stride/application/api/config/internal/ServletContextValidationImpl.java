package io.descoped.stride.application.api.config.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.ServletContextValidation;
import io.descoped.stride.application.api.exception.ExceptionFunction;
import io.descoped.stride.application.api.jackson.JsonElement;

import java.util.Set;

import static java.util.Optional.ofNullable;

public record ServletContextValidationImpl(ObjectNode json) implements ServletContextValidation {

    @Override
    public Set<NamedService> serviceLocatorNamedServices() {
        return JsonElement.ofEphemeral(json)
                .with("requires.namedServices")
                .toSet(node -> {
                    JsonElement namedServiceElement = JsonElement.ofStrict(node);
                    return new NamedService(
                            namedServiceElement.with("type").asString().map(ExceptionFunction.call(() -> s -> Class.forName(s))).orElseThrow(),
                            namedServiceElement.with("named").asString().orElseThrow());
                });
    }

    @Override
    public Set<String> servletContextAttributes() {
        return JsonElement.ofEphemeral(json)
                .with("requires.servletContextAttributes")
                .toSet(JsonNode::asText);
    }

    public record ServletContextValidationBuilder(ObjectNode builder) implements ServletContextValidation.Builder {

        public ServletContextValidationBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Builder requireNamedService(Class<?> type, String named) {
            ObjectNode namedServiceNode = JsonNodeFactory.instance.objectNode();
            namedServiceNode.set("type", builder.textNode(type.getName()));
            namedServiceNode.set("named", builder.textNode(named));
            ArrayNode requiresArrayNode = BuilderHelper.createOrGet(JsonElement.ofDynamic(builder).with("requires").object(), "namedServices");
            requiresArrayNode.add(namedServiceNode);
            return this;
        }

        @Override
        public ServletContextValidation.Builder requireAttribute(String name) {
            ArrayNode requiresArrayNode = BuilderHelper.createOrGet(JsonElement.ofDynamic(builder).with("requires").object(), "servletContextAttributes");
            requiresArrayNode.add(builder.textNode(name));
            return this;
        }

        @Override
        public ServletContextValidation.Builder requireAttributes(Set<String> nameSet) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            ofNullable(nameSet).ifPresent(set -> set.forEach(arrayNode::add));
            JsonElement.ofDynamic(builder).with("requires").object().set("servletContextAttributes", arrayNode);
            return this;
        }

        @Override
        public ServletContextValidationImpl build() {
            return new ServletContextValidationImpl(builder);
        }
    }
}
