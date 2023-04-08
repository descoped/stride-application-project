package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.jackson.JsonCreationStrategy;
import io.descoped.stride.application.jackson.JsonElement;
import io.descoped.stride.application.jackson.JsonMerger;
import no.cantara.config.ApplicationProperties;

import java.util.Objects;

import static java.util.Optional.ofNullable;

/**
 * The ApplicationConfiguration navigates and resolves values in hierachy using jackson objects. If the internal
 * json is updated, this will automatically be reflected by any lookup.
 */
public final class ApplicationConfiguration implements JsonElement {
    private final ObjectNode json;
    private final JsonCreationStrategy strategy;

    private ApplicationConfiguration(ObjectNode json, JsonCreationStrategy strategy) {
        this.json = json;
        this.strategy = strategy;
    }

    @Override
    public JsonNode json() {
        return json;
    }

    @Override
    public JsonCreationStrategy strategy() {
        return strategy;
    }

    private JsonNode nonNullNode() {
        return ofNullable(json).orElseThrow(IllegalStateException::new);
    }

    // Using JsonElementStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST strategy during element navigation
    JsonElement element() {
        return JsonElement.ofEphemeral(nonNullNode());
    }

    public String toString() {
        return nonNullNode().toString();
    }

    public String toPrettyString() {
        return nonNullNode().toPrettyString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ApplicationConfiguration) obj;
        return Objects.equals(this.json, that.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(json);
    }

    // ---------------------------------------------------------------------------------------------------------------

    public Services services() {
        return ofNullable(json)
                .map(node -> node.get("services"))
                .map(ObjectNode.class::cast)
                .map(Services::new)
                .orElse(new Services(JsonNodeFactory.instance.objectNode()));
    }

    public Filters filters() {
        return ofNullable(json)
                .map(node -> node.get("filters"))
                .map(ObjectNode.class::cast)
                .map(Filters::new)
                .orElse(new Filters(JsonNodeFactory.instance.objectNode()));
    }

    public Servlets servlets() {
        return ofNullable(json)
                .map(node -> node.get("servlets"))
                .map(ObjectNode.class::cast)
                .map(Servlets::new)
                .orElse(new Servlets(JsonNodeFactory.instance.objectNode()));
    }

    public Resources resources() {
        return ofNullable(json)
                .map(node -> node.get("resources"))
                .map(ObjectNode.class::cast)
                .map(Resources::new)
                .orElse(new Resources(JsonNodeFactory.instance.objectNode()));
    }

    // ---------------------------------------------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ApplicationProperties applicationProperties;
        private Services.Builder servicesBuilder;
        private Filters.Builder filtersBuilder;
        private Servlets.Builder servletsBuilder;
        private Resources.Builder resourcesBuilder;

        public Builder configuration(ApplicationProperties applicationProperties) {
            this.applicationProperties = applicationProperties;
            return this;
        }

        public Builder services(Services.Builder servicesBuilder) {
            this.servicesBuilder = servicesBuilder;
            return this;
        }

        public Builder filters(Filters.Builder filtersBuilder) {
            this.filtersBuilder = filtersBuilder;
            return this;
        }

        public Builder servlets(Servlets.Builder servletsBuilder) {
            this.servletsBuilder = servletsBuilder;
            return this;
        }

        public Builder resources(Resources.Builder resourcesBuilder) {
            this.resourcesBuilder = resourcesBuilder;
            return this;
        }


        public ApplicationConfiguration build() {
            if (applicationProperties == null) {
                applicationProperties = ApplicationProperties.builder()
                        .classpathPropertiesFile("application-defaults.properties")
                        .defaults()
                        .enableEnvironmentVariables()
                        .enableSystemProperties()
                        .buildAndSetStaticSingleton();
            }

            ApplicationJson applicationJson = new ApplicationJson(applicationProperties);
            ObjectNode jsonConfiguration = (ObjectNode) applicationJson.json();

            // merge deployment builders
            JsonMerger merger = new JsonMerger();
            ObjectNode parentNode = JsonNodeFactory.instance.objectNode();
            if (servicesBuilder != null) {
                ObjectNode servicesJson = parentNode.set("services", servicesBuilder.build().json());
                merger.merge(jsonConfiguration, servicesJson);
            }

            if (filtersBuilder != null) {
                ObjectNode filtersJson = parentNode.set("filters", filtersBuilder.build().json());
                merger.merge(jsonConfiguration, filtersJson);
            }

            if (servletsBuilder != null) {
                ObjectNode servletsJson = parentNode.set("servlets", servletsBuilder.build().json());
                merger.merge(jsonConfiguration, servletsJson);
            }

            if (resourcesBuilder != null) {
                ObjectNode resourcesJson = parentNode.set("resources", resourcesBuilder.build().json());
                merger.merge(jsonConfiguration, resourcesJson);
            }

            System.err.println(jsonConfiguration.toPrettyString());

            return new ApplicationConfiguration(jsonConfiguration, JsonCreationStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    public boolean isVerboseLogging() {
        return element().asBoolean("logging.verbose", false);
    }

    public Server server() {
        return new Server(element().with("server"));
    }

    public Application application() {
        return new Application(element().with("application"), server());
    }

    public record Server(JsonElement element) {

        public String host() {
            return element.with("host").asString("localhost");
        }

        public int port() {
            return element.with("port").asInt(9090);
        }

        public String contextPath() {
            return element.with("context-path").asString("/");
        }
    }

    public record Application(JsonElement element, Server server) {
        public String alias() {
            return element.with("alias").asString("default");
        }

        public String version() {
            return element.with("version").asString("unknown");
        }

        public String url() {
            return element.with("url").asString(String.format("http://%s:%s%s",
                    server.host(), server.port(), server.contextPath()));
        }

        public Cors cors() {
            return new Cors(element.with("cors"));
        }

        public record Cors(JsonElement element) {
            public String headers() {
                return element.with("headers").asString("origin, content-type, accept, authorization");
            }
        }
    }

}
