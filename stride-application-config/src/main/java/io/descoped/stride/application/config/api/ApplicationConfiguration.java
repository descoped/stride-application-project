package io.descoped.stride.application.config.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.api.internal.ApplicationConfigurationJson;
import io.descoped.stride.application.config.api.internal.FiltersImpl;
import io.descoped.stride.application.config.api.internal.ResourcesImpl;
import io.descoped.stride.application.config.api.internal.ServicesImpl;
import io.descoped.stride.application.config.api.internal.ServletContextInitializationImpl;
import io.descoped.stride.application.config.api.internal.ServletsImpl;
import io.descoped.stride.application.jackson.api.JsonCreationStrategy;
import io.descoped.stride.application.jackson.api.JsonElement;
import io.descoped.stride.application.jackson.api.internal.JsonMerger;
import no.cantara.config.ApplicationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Optional.ofNullable;

/**
 * The ApplicationConfiguration navigates and resolves values in hierachy using jackson objects. If the internal
 * json is updated, this will automatically be reflected by any lookup.
 */
public final class ApplicationConfiguration implements JsonElement {

    private final ObjectNode json;
    private final JsonCreationStrategy strategy;
    private final Services services;
    private final Filters filters;
    private final Servlets servlets;
    private final Resources resources;
    private final ServletContextInitialization initializers;

    private ApplicationConfiguration(ObjectNode json, JsonCreationStrategy strategy) {
        this.json = json;
        this.strategy = strategy;

        services = element()
                .with("services")
                .toObjectNode()
                .<Services>map(ServicesImpl::new)
                .orElse(Services.builder().build());

        filters = element()
                .with("filters")
                .toObjectNode()
                .<Filters>map(FiltersImpl::new)
                .orElse(Filters.builder().build());

        servlets = element()
                .with("servlets")
                .toObjectNode()
                .<Servlets>map(ServletsImpl::new)
                .orElse(Servlets.builder().build());

        resources = element()
                .with("resources")
                .toObjectNode()
                .<Resources>map(ResourcesImpl::new)
                .orElse(Resources.builder().build());

        initializers = element()
                .with("initializers")
                .toObjectNode()
                .<ServletContextInitialization>map(ServletContextInitializationImpl::new)
                .orElse(ServletContextInitialization.builder().build());
    }

    @Override
    public JsonNode json() {
        return json.deepCopy();
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

    public Services services() {
        return services;
    }

    public Filters filters() {
        return filters;
    }

    public Servlets servlets() {
        return servlets;
    }

    public Resources resources() {
        return resources;
    }

    public ServletContextInitialization initialization() {
        return initializers;
    }

    public String toPrettyString() {
        return nonNullNode().toPrettyString();
    }

    public String toString() {
        return nonNullNode().toString();
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ApplicationProperties.Builder applicationPropertiesBuilder;
        private Map<String, String> overrideProperties = new LinkedHashMap<>();
        private Services.Builder servicesBuilder;
        private Filters.Builder filtersBuilder;
        private Servlets.Builder servletsBuilder;
        private Resources.Builder resourcesBuilder;
        private ServletContextInitialization.Builder servletContextInitializationBuilder;
        private boolean enableDefault;
        private boolean enableTestDefault;

        public Builder defaults() {
            this.enableDefault = true;
            return this;
        }

        public Builder testDefaults() {
            this.enableTestDefault = true;
            return this;
        }

        public Builder overrideProperty(String name, String value) {
            overrideProperties.put(name, value);
            return null;
        }

        public Builder configuration(ApplicationProperties.Builder applicationPropertiesBuilder) {
            this.applicationPropertiesBuilder = applicationPropertiesBuilder;
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

        public Builder initialization(ServletContextInitialization.Builder servletContextInitializationBuilder) {
            this.servletContextInitializationBuilder = servletContextInitializationBuilder;
            return this;
        }

        public ApplicationConfiguration build() {
            if (enableDefault && applicationPropertiesBuilder == null) {
                applicationPropertiesBuilder = ApplicationProperties.builder()
                        .classpathPropertiesFile("application-defaults.properties")
                        .defaults();
            } else if (enableTestDefault && applicationPropertiesBuilder == null) {
                applicationPropertiesBuilder = ApplicationProperties.builder()
                        .testDefaults();
            } else if (applicationPropertiesBuilder == null) {
                applicationPropertiesBuilder = ApplicationProperties.builder();
            }

            // apply override properties
            overrideProperties.forEach((k, v) -> applicationPropertiesBuilder.property(k, v));

            ApplicationProperties applicationProperties = enableDefault ?
                    applicationPropertiesBuilder.buildAndSetStaticSingleton() :
                    applicationPropertiesBuilder.build();

            ApplicationConfigurationJson applicationConfigurationJson = new ApplicationConfigurationJson(applicationProperties);
            ObjectNode jsonConfiguration = (ObjectNode) applicationConfigurationJson.json();

            // merge deployment builders
            JsonMerger merger = new JsonMerger();
            if (servicesBuilder != null) {
                mergeBuilder(jsonConfiguration, merger, "services", servicesBuilder.build().json());
            }

            if (filtersBuilder != null) {
                mergeBuilder(jsonConfiguration, merger, "filters", filtersBuilder.build().json());
            }

            if (servletsBuilder != null) {
                mergeBuilder(jsonConfiguration, merger, "servlets", servletsBuilder.build().json());
            }

            if (resourcesBuilder != null) {
                mergeBuilder(jsonConfiguration, merger, "resources", resourcesBuilder.build().json());
            }

            if (servletContextInitializationBuilder != null) {
                mergeBuilder(jsonConfiguration, merger, "initializers", servletContextInitializationBuilder.build().json());
            }

            //System.out.printf("config:\n%s\n", jsonConfiguration.toPrettyString());

            return new ApplicationConfiguration(jsonConfiguration, JsonCreationStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST);
        }

        private void mergeBuilder(ObjectNode jsonConfiguration, JsonMerger merger, String fieldName, ObjectNode jsonBuilder) {
            if (jsonConfiguration.has(fieldName)) {
                ObjectNode parentNode = JsonNodeFactory.instance.objectNode();
                ObjectNode jsonNode = parentNode.set(fieldName, jsonBuilder);
                merger.merge(jsonConfiguration, jsonNode);
            } else {
                jsonConfiguration.set(fieldName, jsonBuilder);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    public boolean isVerboseLogging() {
        return element().asBoolean("logging.verbose", false);
    }

    // ---------------------------------------------------------------------------------------------------------------

    public Server server() {
        return new Server(element().with("services.jetty.server.config"));
    }

    public record Server(JsonElement element) {

        public String host() {
            return element.asString("host", "localhost");
        }

        public int port() {
            return element.asInt("port", 9090);
        }

        public String contextPath() {
            return element.asString("context-path", "/");
        }

        public int minThreads() {
            return element.asInt("minThreads", 10);
        }

        public int maxThreads() {
            return element.asInt("maxThreads", 150);
        }

        public int outputBufferSize() {
            return element.asInt("outputBufferSize", 32768);
        }

        public int requestHeaderSize() {
            return element.asInt("requestHeaderSize", 16384);
        }

        public boolean isHttp2Enabled() {
            return element.asBoolean("http2.enabled", false);
        }

        public long idleTimeout() {
            return Duration.parse(element.asString("idleTimeout", "PT-1s")).toSeconds();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    public Jersey jersey() {
        return new Jersey(element().with("services.jersey.server.config"));
    }


    public record Jersey(JsonElement jsonElement) {
        public Collection<String> mediaTypes() {
            List<String> mediaTypesList = new ArrayList<>();
            jsonElement.with("mediaTypes")
                    .toMap(JsonNode::asText)
                    .forEach((key, mimeType) -> mediaTypesList.add(key + ":" + mimeType));
            return mediaTypesList;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    public Application application() {
        return new Application(element().with("application"), server());
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

        // ---------------------------------------------------------------------------------------------------------------

        public Cors cors() {
            return new Cors(element.with("cors"));
        }

        public record Cors(JsonElement element) {
            public String headers() {
                return element.asString("headers", "origin, content-type, accept, authorization");
            }
        }
    }
}
