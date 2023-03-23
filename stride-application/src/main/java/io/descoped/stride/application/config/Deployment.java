package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.jackson.JsonElement;

public record Deployment(ObjectNode json) {

    public Servlets servlets() {
        return new Servlets(JsonElement.of(json).with("servlets").array());
    }

    public record Builder(ObjectNode builder) {

        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder servlets(Servlets.Builder servletsBuilder) {
            builder.set("servlets", servletsBuilder.build().json());
            return this;
        }

        public Deployment build() {
            return new Deployment(builder);
        }
    }

}
