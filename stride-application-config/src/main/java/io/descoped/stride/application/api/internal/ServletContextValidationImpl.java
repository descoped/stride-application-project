package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.ServletContextValidation;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Optional.ofNullable;

public record ServletContextValidationImpl(ObjectNode json) implements ServletContextValidation {

    @Override
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }


    public record ServletContextValidationBuilder(ObjectNode builder) implements ServletContextValidation.Builder {
        public ServletContextValidationBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public ServletContextValidation.Builder require(String named) {
            ArrayNode requiresArrayNode = BuilderHelper.createOrGet(builder, "requires");
            requiresArrayNode.add(builder.textNode(named));

            return this;
        }

        @Override
        public ServletContextValidation.Builder requires(Set<String> named) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            ofNullable(named).ifPresent(set -> set.forEach(arrayNode::add));
            builder.set("requires", arrayNode);
            return this;
        }

        @Override
        public ServletContextValidationImpl build() {
            return new ServletContextValidationImpl(builder);
        }
    }
}
