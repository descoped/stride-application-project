package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.jackson.JsonElement;
import no.cantara.config.ApplicationProperties;

public final class Deployment {
    private final ApplicationProperties applicationProperties;
    private final ObjectNode json;

    public Deployment(ApplicationProperties applicationProperties, ObjectNode json) {
        this.applicationProperties = applicationProperties;
        this.json = json;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ApplicationProperties properties() {
        return applicationProperties;
    }

    public Services services() {
        return new Services(JsonElement.of(json).with("services").array());
    }

    public Filters filters() {
        return new Filters(JsonElement.of(json).with("filters").array());
    }

    public Servlets servlets() {
        return new Servlets(JsonElement.of(json).with("servlets").array());
    }

    public ObjectNode json() {
        return json;
    }

    public static final class Builder {
        private ApplicationProperties applicationProperties;

        private final ObjectNode builder;

        public Builder(ObjectNode builder) {
            this.builder = builder;
        }

        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder configuration(ApplicationProperties applicationProperties) {
            this.applicationProperties = applicationProperties;
            return this;
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
            return new Deployment(applicationProperties, builder);
        }
    }
}
