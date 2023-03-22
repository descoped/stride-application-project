package io.descoped.stride.application.config;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Servlet;

import java.util.Objects;

public record Servlets(ArrayNode json) {

    /**
     * Lookup array element by servlet-name
     *
     * @param name servletName
     * @return Node that contains name
     */
    public JsonElement servletName(String name) {
        Objects.requireNonNull(name);
        for (JsonNode itemNode : json) {
            JsonElement itemElement = JsonElement.ofEphemeral(itemNode);
            if (name.equals(itemElement.asString("servletName", null))) {
                return itemElement;
            }
        }
        return JsonElement.ofEphemeral(null);
    }

    public JsonElement servletClass(String className) {
        Objects.requireNonNull(className);
        for (JsonNode itemNode : json) {
            JsonElement itemElement = JsonElement.ofEphemeral(itemNode);
            if (className.equals(itemElement.asString("servletClass", null))) {
                return itemElement;
            }
        }
        return JsonElement.ofEphemeral(null);
    }

    public record Builder(ArrayNode builder) {

        public Builder() {
            this(JsonNodeFactory.instance.arrayNode());
        }

        public Builder servlet(String name, Class<? extends Servlet> servletClass, String pathSpec) {
            ObjectNode servletNode = JsonNodeFactory.instance.objectNode();
            ofNullable(name).map(builder::textNode).map(node -> servletNode.set("servletName", node));
            servletNode.set("servletClass", builder.textNode(servletClass.getName()));
            servletNode.set("pathSpec", builder.textNode(pathSpec));
            builder.add(servletNode);
            return this;
        }

        public Servlets build() {
            return new Servlets(builder);
        }
    }

}
