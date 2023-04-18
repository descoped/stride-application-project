package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Optional.ofNullable;

public record ServletContextValidation(ObjectNode json) {

    public static Builder builder() {
        return new Builder();
    }

    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }


    public record Builder(ObjectNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder require(String named) {
            ArrayNode requiresArrayNode = ConfigHelper.createOrGet(builder, "requires");
            requiresArrayNode.add(builder.textNode(named));

            return this;
        }

        public Builder requires(Set<String> named) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            ofNullable(named).ifPresent(set -> set.forEach(arrayNode::add));
            builder.set("requires", arrayNode);
            return this;
        }

        public ServletContextValidation build() {
            return new ServletContextValidation(builder);
        }
    }
}
