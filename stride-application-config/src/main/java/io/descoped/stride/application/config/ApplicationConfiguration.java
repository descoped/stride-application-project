package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.config.DynamicConfiguration;
import io.descoped.stride.application.config.internal.ApplicationConfigurationJson;
import io.descoped.stride.application.config.internal.FiltersImpl;
import io.descoped.stride.application.config.internal.PropertyFlattener;
import io.descoped.stride.application.config.internal.ResourcesImpl;
import io.descoped.stride.application.config.internal.ServicesImpl;
import io.descoped.stride.application.config.internal.ServletContextInitializationImpl;
import io.descoped.stride.application.config.internal.ServletsImpl;
import io.descoped.stride.application.jackson.JsonCreationStrategy;
import io.descoped.stride.application.jackson.JsonElement;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

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

    /**
     * Starts a configuration. Sources are layered, later wins:
     * <ol>
     *     <li>Stride's {@code application-defaults.properties}, which declares every key Stride reads;</li>
     *     <li>services, filters, servlets, resources and initializers declared in code with the builders;</li>
     *     <li>the application's sources: {@link Builder#defaults()}, {@link Builder#testDefaults()} or
     *     {@link Builder#configuration}; with the presets these are files, then environment variables, then system
     *     properties;</li>
     *     <li>{@link Builder#overrideProperty} values.</li>
     * </ol>
     * Code decides which components exist; whoever deploys the application can still change their values.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private static final String DEFAULTS = "application-defaults.properties";

        private Consumer<DynamicConfiguration.Builder> sources;
        private Map<String, String> overrideProperties = new LinkedHashMap<>();
        private Services.Builder servicesBuilder;
        private Filters.Builder filtersBuilder;
        private Servlets.Builder servletsBuilder;
        private Resources.Builder resourcesBuilder;
        private ServletContextInitialization.Builder servletContextInitializationBuilder;
        private boolean enableDefault;
        private boolean enableTestDefault;

        /**
         * On top of Stride's defaults: reads {@code application.properties} from the class path and
         * {@code local_override.properties} from the working directory, then lets environment variables and system
         * properties override any declared key. Ignored when {@link #configuration} supplies the sources.
         */
        public Builder defaults() {
            this.enableDefault = true;
            return this;
        }

        /**
         * As {@link #defaults()}, with {@code test_override.properties} from the class path in place of
         * {@code local_override.properties}.
         */
        public Builder testDefaults() {
            this.enableTestDefault = true;
            return this;
        }

        public Builder overrideProperty(String name, String value) {
            overrideProperties.put(name, value);
            return this;
        }

        /**
         * Supplies the application's configuration sources, in place of {@link #defaults()} or
         * {@link #testDefaults()}. Stride's own defaults are always the first source, so every key Stride reads is
         * declared and can be overridden by {@code environment()} or {@code systemProperties()} when the application
         * adds them. Values given with {@link #overrideProperty} are added last and win.
         */
        public Builder configuration(Consumer<DynamicConfiguration.Builder> sources) {
            this.sources = Objects.requireNonNull(sources, "sources");
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
            DynamicConfiguration.Builder builder = DynamicConfiguration.builder();

            // 0. the settings of filters declared in code, at their defaults. Lowest of all, so everything else
            //    wins, but declared, so each can be overridden by environment variable or system property.
            if (filtersBuilder != null) {
                Map<String, String> implied = new LinkedHashMap<>();
                for (Filter filter : filtersBuilder.build().iterator()) {
                    String prefix = "filters." + filter.name() + ".config.";
                    implied.put(prefix + "pathSpec", Filter.DEFAULT_PATH_SPEC);
                    implied.put(prefix + "order", Integer.toString(Filter.DEFAULT_ORDER));
                    implied.put(prefix + "dispatches.0", "REQUEST");
                }
                if (!implied.isEmpty()) {
                    builder.values(implied);
                }
            }

            // 1. Stride's defaults: they declare every key Stride reads
            URL defaults = Objects.requireNonNull(ApplicationConfiguration.class.getResource("/" + DEFAULTS),
                    DEFAULTS + " is missing from stride-application-config");
            builder.resource(defaults);

            // 2. what the application declares in code: it decides which components exist, and its values are
            //    defaults that every later source can override
            Map<String, String> declared = new LinkedHashMap<>();
            if (servicesBuilder != null) {
                PropertyFlattener.flatten("services", servicesBuilder.build().json(), declared);
            }
            if (filtersBuilder != null) {
                PropertyFlattener.flatten("filters", filtersBuilder.build().json(), declared);
            }
            if (servletsBuilder != null) {
                PropertyFlattener.flatten("servlets", servletsBuilder.build().json(), declared);
            }
            if (resourcesBuilder != null) {
                PropertyFlattener.flatten("resources", resourcesBuilder.build().json(), declared);
            }
            if (servletContextInitializationBuilder != null) {
                PropertyFlattener.flatten("initializers", servletContextInitializationBuilder.build().json(), declared);
            }
            if (!declared.isEmpty()) {
                builder.values(declared);
            }

            // 3. the application's sources: files, then environment variables, then system properties
            if (sources != null) {
                sources.accept(builder);
            } else if (enableDefault || enableTestDefault) {
                builder.classpath("application.properties");
                if (enableDefault) {
                    builder.file(Path.of("local_override.properties"));
                } else {
                    builder.classpath("test_override.properties");
                }
                builder.environment().systemProperties();
            }

            // 4. override properties are added last, so they win over every source
            if (!overrideProperties.isEmpty()) {
                builder.values(overrideProperties);
            }

            ObjectNode jsonConfiguration = (ObjectNode) new ApplicationConfigurationJson(builder.build().asMap()).json();
            return new ApplicationConfiguration(jsonConfiguration, JsonCreationStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    public boolean isVerboseLogging() {
        return element().requireBoolean("logging.verbose");
    }

    // ---------------------------------------------------------------------------------------------------------------

    public SecurityConfiguration security() {
        return SecurityConfiguration.from(this);
    }

    public Server server() {
        return new Server(element().with("services.jetty.server.config"));
    }

    public record Server(JsonElement element) {

        public String host() {
            return element.requireString("host");
        }

        public int port() {
            return element.requireInt("port");
        }

        public String contextPath() {
            return element.requireString("context-path");
        }

        public int minThreads() {
            return element.requireInt("minThreads");
        }

        public int maxThreads() {
            return element.requireInt("maxThreads");
        }

        public int outputBufferSize() {
            return element.requireInt("outputBufferSize");
        }

        public int requestHeaderSize() {
            return element.requireInt("requestHeaderSize");
        }

        public boolean isHttp2Enabled() {
            return element.requireBoolean("http2.enabled");
        }

        public long idleTimeout() {
            return Duration.parse(element.requireString("idleTimeout")).toMillis();
        }

        /**
         * How long, in milliseconds, requests that are running when the server stops get to finish.
         */
        public long stopTimeout() {
            return Duration.parse(element.requireString("stopTimeout")).toMillis();
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
            return element.requireString("alias");
        }

        public String version() {
            return element.requireString("version");
        }

        /**
         * The address clients reach the application at, when it has been configured. Stride cannot know it: the
         * application may listen on a port picked by the operating system and sit behind a proxy.
         */
        public Optional<String> url() {
            return Optional.of(element.requireString("url")).filter(url -> !url.isBlank());
        }
    }
}
