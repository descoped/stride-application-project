package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.exception.ExceptionFunction;
import io.descoped.stride.application.jackson.JsonElement;

import java.util.Optional;

import static java.util.Optional.ofNullable;

public record Resources(ArrayNode json) {
    public static Builder builder() {
        return new Builder();
    }

    public static Resource.Builder resourceBuilder() {
        return new Resource.Builder();
    }

    private Optional<JsonNode> findNode(String withProperty, String equalTo) {
        if (equalTo == null) {
            return Optional.empty();
        }
        for (JsonNode itemNode : json) {
            JsonElement itemElement = JsonElement.ofEphemeral(itemNode);
            if (equalTo.equals(itemElement.asString(withProperty, null))) {
                return itemElement.optionalNode();
            }
        }
        return Optional.empty();
    }

    /**
     * Lookup resource by class
     *
     * @param className servlet class
     * @return Servlet config
     */
    public Optional<Resource> clazz(String className) {
        return findNode("resourceClass", className).map(ObjectNode.class::cast).map(Resource::new);
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Builder(ArrayNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.arrayNode());
        }

        public Builder resource(Resource.Builder resourceBuilder) {
            builder.add(resourceBuilder.build().json);
            return this;
        }

        public Resources build() {
            return new Resources(builder);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Resource(ObjectNode json) {
        public String className() {
            return ofNullable(json)
                    .map(node -> node.get("resourceClass"))
                    .map(JsonNode::asText)
                    .orElse(null);
        }

        @SuppressWarnings("unchecked")
        public <R> Class<R> clazz() {
            return ofNullable(className())
                    .map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s))) // deal with hard exception
                    .orElse(null);
        }

        // ------------------------------------------------------------------------------------------------------------

        public record Builder(ObjectNode builder) {
            public Builder() {
                this(JsonNodeFactory.instance.objectNode());
            }

            public Builder className(String resourceClassName) {
                builder.set("resourceClass", builder.textNode(resourceClassName));
                return this;
            }

            public <R> Builder clazz(Class<R> resourceClass) {
                return className(resourceClass.getName());
            }

            public Resource build() {
                return new Resource(builder);
            }
        }
    }
}
