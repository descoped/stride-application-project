package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.exception.ExceptionFunction;
import io.descoped.stride.application.jackson.JsonElement;

import java.util.Optional;

import static java.util.Optional.ofNullable;

public record Servlets(ArrayNode json) {
    public static Builder builder() {
        return new Builder();
    }

    public static Servlet.Builder servletBuilder() {
        return new Servlet.Builder();
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
     * Lookup servlet by name
     *
     * @param name servletName
     * @return Servlet config
     */
    public Optional<Servlet> servletName(String name) {
        return findNode("servletName", name).map(ObjectNode.class::cast).map(Servlet::new);
    }

    /**
     * Lookup servlet by class
     *
     * @param className servlet class
     * @return Servlet config
     */
    public Optional<Servlet> servletClass(String className) {
        return findNode("servletClass", className).map(ObjectNode.class::cast).map(Servlet::new);
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Builder(ArrayNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.arrayNode());
        }

        public Builder servlet(Servlet.Builder servletBuilder) {
            builder.add(servletBuilder.build().json);
            return this;
        }

        public Servlets build() {
            return new Servlets(builder);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Servlet(ObjectNode json) {
        public String name() {
            return ofNullable(json)
                    .map(node -> node.get("servletName"))
                    .map(JsonNode::asText)
                    .orElse(null);
        }

        public String className() {
            return ofNullable(json)
                    .map(node -> node.get("servletClass"))
                    .map(JsonNode::asText)
                    .orElse(null);
        }

        @SuppressWarnings("unchecked")
        public <R extends Servlet> Class<R> clazz() {
            return ofNullable(className())
                    .map(ExceptionFunction.call(() -> s -> (Class<R>) Class.forName(s))) // deal with hard exception
                    .orElse(null);
        }

        public String pathSpec() {
            return ofNullable(json)
                    .map(node -> node.get("pathSpec"))
                    .map(JsonNode::asText)
                    .orElse(null);
        }

        // ------------------------------------------------------------------------------------------------------------

        public record Builder(ObjectNode builder) {
            public Builder() {
                this(JsonNodeFactory.instance.objectNode());
            }

            public Builder name(String servletName) {
                ofNullable(servletName).map(builder::textNode).map(node -> builder.set("servletName", node));
                return this;
            }

            public Builder className(String servletClassName) {
                builder.set("servletClass", builder.textNode(servletClassName));
                return this;
            }

            public Builder clazz(Class<? extends jakarta.servlet.Servlet> servletClass) {
                return className(servletClass.getName());
            }

            public Builder pathSpec(String pathSpec) {
                builder.set("pathSpec", builder.textNode(pathSpec));
                return this;
            }

            public Servlet build() {
                return new Servlet(builder);
            }
        }
    }
}
