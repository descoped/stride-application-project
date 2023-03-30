package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.cantara.config.ApplicationProperties;

import static java.util.Optional.ofNullable;

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
        return ofNullable(json)
                .map(node -> node.get("services"))
                .map(ArrayNode.class::cast)
                .map(Services::new)
                .orElse(new Services(JsonNodeFactory.instance.arrayNode()));
    }

    public Filters filters() {
        return ofNullable(json)
                .map(node -> node.get("filters"))
                .map(ArrayNode.class::cast)
                .map(Filters::new)
                .orElse(new Filters(JsonNodeFactory.instance.arrayNode()));
    }

    public Servlets servlets() {
        return ofNullable(json)
                .map(node -> node.get("servlets"))
                .map(ArrayNode.class::cast)
                .map(Servlets::new)
                .orElse(new Servlets(JsonNodeFactory.instance.arrayNode()));
    }

    public Resources resources() {
        return ofNullable(json)
                .map(node -> node.get("resources"))
                .map(ArrayNode.class::cast)
                .map(Resources::new)
                .orElse(new Resources(JsonNodeFactory.instance.arrayNode()));
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

        public Builder filters(Filters.Builder filtersBuilder) {
            builder.set("filters", filtersBuilder.build().json());
            return this;
        }

        public Builder servlets(Servlets.Builder servletsBuilder) {
            builder.set("servlets", servletsBuilder.build().json());
            return this;
        }

        public Builder resources(Resources.Builder resourcesBuilder) {
            builder.set("resources", resourcesBuilder.build().json());
            return this;
        }

        public Deployment build() {
            return new Deployment(applicationProperties, builder);
        }
    }
}
