package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.exception.ExceptionFunction;

import static java.util.Optional.ofNullable;

public record Arg(ObjectNode json) {

    public static Arg.Builder builder() {
        return new Arg.Builder();
    }

    public Class<?> clazz() {
        return ofNullable(json)
                .map(node -> node.get("class"))
                .map(JsonNode::asText)
                .map(ExceptionFunction.call(() -> s -> Class.forName(s)))
                .orElse(null);
    }

    public String named() {
        return ofNullable(json)
                .map(node -> node.get("named"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    public record Builder(ObjectNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder arg(Class<?> clazz, String named) {
            builder.set("class", builder.textNode(clazz.getName()));
            builder.set("named", builder.textNode(named));
            return this;
        }

        public Arg build() {
            return new Arg(builder);
        }
    }
}
