package io.descoped.stride.application;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.descoped.stride.application.api.config.ApplicationConfiguration;
import io.descoped.stride.application.core.DefaultExceptionServletFilter;
import io.descoped.stride.application.core.InstanceFactory;
import io.descoped.stride.application.cors.ApplicationCORSServletFilter;
import io.descoped.stride.application.openapi.ApplicationOpenApiResource;
import io.descoped.stride.application.openapi.ApplicationOpenApiSpecFilter;
import io.dropwizard.metrics.jetty11.InstrumentedConnectionFactory;
import io.dropwizard.metrics.jetty11.InstrumentedHttpChannelListener;
import io.dropwizard.metrics.jetty11.InstrumentedQueuedThreadPool;
import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.HealthCheckServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.ws.rs.ext.ContextResolver;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private final ApplicationConfiguration configuration;
    private final InstanceFactory instanceFactory;
    private final AtomicReference<Server> jettyServerRef = new AtomicReference<>();
    private final List<FilterSpec> filterSpecs = new CopyOnWriteArrayList<>();
    private final ResourceConfig resourceConfig; // jakarta.ws.rs.core.Application
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final ServiceLocator serviceLocator;

    public Application(ApplicationConfiguration configuration, InstanceFactory instanceFactory) {
        this.configuration = configuration;
        this.instanceFactory = instanceFactory;
        serviceLocator = ServiceLocatorFactory.getInstance().create(null);
        resourceConfig = new ResourceConfig();
        resourceConfig.property("jersey.config.server.wadl.disableWadl", "true");
        instanceFactory.put(jakarta.ws.rs.core.Application.class, resourceConfig);
        instanceFactory.put(ResourceConfig.class, resourceConfig);
    }

    public String getHost() {
        String host = ((ServerConnector) jettyServerRef.get().getConnectors()[0]).getHost();
        return host == null ? "localhost" : host;
    }

    public int getPort() {
        int port = ((ServerConnector) jettyServerRef.get().getConnectors()[0]).getPort();
        return port;
    }

    public int getBoundPort() {
        int port = ((ServerConnector) jettyServerRef.get().getConnectors()[0]).getLocalPort();
        return port;
    }


    // The ServerConnector is closed during Application.stop()
    @SuppressWarnings("resource")
    void doStart() {
        try {
            QueuedThreadPool threadPool;
            ConnectionFactory connectionFactory;
            EventListener httpChannelListener = null;
            MetricRegistry jettyMetricRegistry = instanceFactory.getOrNull("metrics.jetty");
            if (jettyMetricRegistry != null) {
                // jetty with metrics instrumentation
                threadPool = new InstrumentedQueuedThreadPool(jettyMetricRegistry);
                Timer connectionFactoryTimer = new Timer();
                Counter connectionFactoryCounter = new Counter();
                jettyMetricRegistry.register("connection-factory.connection-duration",
                        connectionFactoryTimer);
                jettyMetricRegistry.register("connection-factory.connections", connectionFactoryCounter);
                connectionFactory = new InstrumentedConnectionFactory(new HttpConnectionFactory(),
                        connectionFactoryTimer, connectionFactoryCounter);
                httpChannelListener = new InstrumentedHttpChannelListener(jettyMetricRegistry,
                        "http-channel");
            } else {
                // plain jetty
                threadPool = new QueuedThreadPool();
                connectionFactory = new HttpConnectionFactory();
            }

            String host = configuration.server().host();
            int port = configuration.server().port();
            if (port == 0) {
                port = 9090;
            }

            final Server server = new Server(threadPool);
            jettyServerRef.set(server);
            ServerConnector connector = new ServerConnector(server, connectionFactory);
            connector.setHost(host);
            connector.setPort(port);
            if (httpChannelListener != null) {
                connector.addEventListener(httpChannelListener);
            }
            server.setConnectors(new Connector[]{connector});
            instanceFactory.put(Server.class, server);
            ResourceConfig resourceConfig = ResourceConfig.forApplication(this.resourceConfig);
            ServletContextHandler servletContextHandler = createServletContextHandler(resourceConfig);
            instanceFactory.put(ServletContextHandler.class, servletContextHandler);
            server.setHandler(servletContextHandler);
            server.start();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ServletContextHandler createServletContextHandler(ResourceConfig resourceConfig) {
        Objects.requireNonNull(resourceConfig);
        ServletContextHandler servletContextHandler = new ServletContextHandler(
                ServletContextHandler.SESSIONS);
        String contextPath = normalizeContextPath(configuration.server().contextPath());
        for (FilterSpec filterSpec : filterSpecs) {
            FilterHolder filterHolder = new FilterHolder(filterSpec.filter);
            servletContextHandler.addFilter(filterHolder, filterSpec.pathSpec, filterSpec.dispatches);
        }
        AdminServlet adminServlet = instanceFactory.getOrNull(AdminServlet.class);
        if (adminServlet != null) {
            MetricRegistry baseMetricRegistry = instanceFactory.getOrNull(
                    "metrics.base");
            if (baseMetricRegistry != null) {
                servletContextHandler.getServletContext()
                        .setAttribute(MetricsServlet.METRICS_REGISTRY, baseMetricRegistry);
            }
            HealthCheckRegistry healthCheckRegistry = instanceFactory.getOrNull(
                    HealthCheckRegistry.class);
            if (healthCheckRegistry != null) {
                servletContextHandler.getServletContext()
                        .setAttribute(HealthCheckServlet.HEALTH_CHECK_REGISTRY, healthCheckRegistry);
            }
            servletContextHandler.addServlet(new ServletHolder(adminServlet), "/admin/*");
        }
        MetricRegistry appMetricsRegistry = instanceFactory.getOrNull(MetricRegistry.class);
        if (appMetricsRegistry != null) {
            servletContextHandler.addServlet(new ServletHolder(new MetricsServlet(appMetricsRegistry)),
                    "/admin/metrics/app/*");
        }
        MetricRegistry jerseyMetricsRegistry = instanceFactory.getOrNull("metrics.jersey");
        if (jerseyMetricsRegistry != null) {
            servletContextHandler.addServlet(new ServletHolder(new MetricsServlet(jerseyMetricsRegistry)),
                    "/admin/metrics/jersey/*");
        }
        MetricRegistry jettyMetricsRegistry = instanceFactory.getOrNull("metrics.jetty");
        if (jettyMetricsRegistry != null) {
            servletContextHandler.addServlet(new ServletHolder(new MetricsServlet(jettyMetricsRegistry)),
                    "/admin/metrics/jetty/*");
        }
        MetricRegistry jvmMetricsRegistry = instanceFactory.getOrNull("metrics.jvm");
        if (jvmMetricsRegistry != null) {
            servletContextHandler.addServlet(new ServletHolder(new MetricsServlet(jvmMetricsRegistry)),
                    "/admin/metrics/jvm/*");
        }
        ServletContainer jerseyServlet = new ServletContainer(resourceConfig);
        instanceFactory.put(ServletContainer.class, jerseyServlet);
        servletContextHandler.addServlet(new ServletHolder(jerseyServlet), contextPath + "/*");
        return servletContextHandler;
    }

    public <T> T init(String key, Supplier<T> init) {
        T instance = init.get();
        instanceFactory.put(key, instance);
        return instance;
    }

    public <T extends Filter> T initAndAddServletFilter(Class<T> clazz,
                                                        Supplier<T> filterSupplier,
                                                        String pathSpec,
                                                        EnumSet<DispatcherType> dispatches) {
        return initAndAddServletFilter(clazz.getName(), filterSupplier, pathSpec, dispatches);
    }

    public <T extends Filter> T initAndAddServletFilter(String key,
                                                        Supplier<T> filterSupplier,
                                                        String pathSpec,
                                                        EnumSet<DispatcherType> dispatches) {
        T instance = init(key, filterSupplier);
        filterSpecs.add(new FilterSpec(key, instance, pathSpec, dispatches));
        return instance;
    }

    public <T> T initAndRegisterJaxRsWsComponent(Class<T> clazz, Supplier<T> init) {
        return initAndRegisterJaxRsWsComponent(clazz.getName(), init);
    }

    public <T> T initAndRegisterJaxRsWsComponent(String key, Supplier<T> init) {
        T instance = init(key, init);
        resourceConfig.register(instance);
        return instance;
    }

    public static void printStackTrace() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        int skip = 2;
        for (StackTraceElement ste : st) {
            if (skip > 0) {
                skip--;
                continue;
            }
            pw.println("    " + ste.toString());
        }
        System.out.printf("StackTrace:%n%s%n", sw.toString());
    }

    public void initBuiltinDefaults() {
        initAndRegisterJaxRsWsComponent("jackson", this::createJacksonMapperProvider);

        initAndAddServletFilter(DefaultExceptionServletFilter.class, DefaultExceptionServletFilter::new,
                "/*", EnumSet.allOf(DispatcherType.class));

        final ApplicationCORSServletFilter applicationCORSServletFilter = initAndAddServletFilter(
                ApplicationCORSServletFilter.class,
                this::configureCORSFilter,
                "/*",
                EnumSet.allOf(DispatcherType.class));

        initAndRegisterJaxRsWsComponent(ApplicationOpenApiResource.class, this::createOpenApiResource);
    }

    private ApplicationCORSServletFilter configureCORSFilter() {
        final ApplicationCORSServletFilter.Builder builder = ApplicationCORSServletFilter.builder();
        builder.headers(configuration.application().cors().headers());
        return builder.build();
    }

    public void start() {
        if (initialized.compareAndSet(false, true)) {
            doStart();
        }
    }

    public void stop() {
        if (initialized.compareAndSet(true, false)) {
            try {
                jettyServerRef.get().stop();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    String normalizeContextPath(String contextPath) {
        Objects.requireNonNull(contextPath);
        String c = contextPath;
        // trim leading slashes
        while (c.startsWith("/")) {
            c = c.substring(1);
        }
        // trim trailing slashes
        while (c.endsWith("/")) {
            c = c.substring(0, c.length() - 1);
        }
        // add single leading slash
        if (c.length() > 0) {
            c = "/" + c;
        }
        return c;
    }

    public InstanceFactory instanceFactory() {
        return instanceFactory;
    }


    protected static class FilterSpec {

        protected final String key;
        protected final Filter filter;
        protected final String pathSpec;
        protected final EnumSet<DispatcherType> dispatches;

        public FilterSpec(String key, Filter filter, String pathSpec,
                          EnumSet<DispatcherType> dispatches) {
            this.key = key;
            this.filter = filter;
            this.pathSpec = pathSpec;
            this.dispatches = dispatches;
        }
    }

    private ContextResolver<ObjectMapper> createJacksonMapperProvider() {
        return new ContextResolver<>() {
            private static final ObjectMapper mapper = new ObjectMapper();

            static {
                mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                mapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
                mapper.enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature());
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                mapper.findAndRegisterModules();
            }

            @Override
            public ObjectMapper getContext(Class<?> type) {
                return mapper;
            }
        };
    }


    private ApplicationOpenApiResource createOpenApiResource() {
        Info info = new Info()
                .title(configuration.application().alias() + " API")
                .version(configuration.application().version());
        String contextPath = normalizeContextPath(configuration.server().contextPath());
        OpenAPI openAPI = new OpenAPI()
                .info(info);
        String applicationUrl = configuration.application().url();
        if (applicationUrl != null) {
            openAPI.addServersItem(new io.swagger.v3.oas.models.servers.Server().url(applicationUrl));
        }
        openAPI.addServersItem(new io.swagger.v3.oas.models.servers.Server() {
            @Override
            public String getUrl() {
                return "http://localhost:" + getBoundPort() + contextPath;
            }
        });
        Map<String, SecurityScheme> securitySchemes = new LinkedHashMap<>();
        securitySchemes.put("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .name("Authorization")
                .in(SecurityScheme.In.HEADER)
                .scheme("bearer"));
        openAPI.components(new Components().securitySchemes(securitySchemes));
        openAPI.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
        SwaggerConfiguration oasConfig = new SwaggerConfiguration()
                .openAPI(openAPI)
                .filterClass(ApplicationOpenApiSpecFilter.class.getName())
                .prettyPrint(true);
        ApplicationOpenApiResource openApiResource = (ApplicationOpenApiResource) new ApplicationOpenApiResource()
                .openApiConfiguration(oasConfig);
        return openApiResource;
    }
}
