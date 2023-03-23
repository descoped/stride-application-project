package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.jackson.JsonElement;

public record Deployment(ObjectNode json) {

    public static Builder builder() {
        return new Builder();
    }

    public Services services() {
        return new Services(JsonElement.of(json).with("services").array());
    }

    public Servlets servlets() {
        return new Servlets(JsonElement.of(json).with("servlets").array());
    }

    public Filters filters() {
        return new Filters(JsonElement.of(json).with("filters").array());
    }

    public record Builder(ObjectNode builder) {

        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder services(Services.Builder servicesBuilder) {
            builder.set("services", servicesBuilder.build().json());
            return this;
        }

        public Builder servlets(Servlets.Builder servletsBuilder) {
            builder.set("servlets", servletsBuilder.build().json());
            return this;
        }

        public Builder filters(Filters.Builder filtersBuilder) {
            builder.set("filters", filtersBuilder.build().json());
            return this;
        }

        public Deployment build() {
            return new Deployment(builder);
        }
    }

}
