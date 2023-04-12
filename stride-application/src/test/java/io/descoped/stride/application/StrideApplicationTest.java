package io.descoped.stride.application;

import com.codahale.metrics.DefaultSettableGauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.jersey3.MetricsFeature;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Args;
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.config.Servlet;
import io.descoped.stride.application.config.ServletContext;
import io.descoped.stride.application.config.ServletContextInitializer;
import io.descoped.stride.application.config.Servlets;
import io.descoped.stride.application.cors.ApplicationCORSServletFilter;
import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.HealthCheckServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import jakarta.inject.Inject;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevelController;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StrideApplicationTest {

    private static final Logger log = LoggerFactory.getLogger(StrideApplicationTest.class);

    static class MetricsServiceInitializer implements ServletContextInitializer {

        private static final Logger log = LoggerFactory.getLogger(MetricsServiceInitializer.class);

        @Inject
        private ApplicationConfiguration configuration;

        @Override
        public void initialize(ServiceLocator locator, ContextHandler.Context context) {
            MetricRegistry baseRegistry = new MetricRegistry();

            MetricRegistry appRegistry = new MetricRegistry();
            appRegistry.register("name", new DefaultSettableGauge<>(configuration.application().alias()));
            baseRegistry.register("app", appRegistry);

            MetricRegistry jettyRegistry = new MetricRegistry();
            baseRegistry.register("jetty", jettyRegistry);

            MetricRegistry jerseyRegistry = new MetricRegistry();
            baseRegistry.register("jersey", jerseyRegistry);

            ServiceLocatorUtilities.addOneConstant(locator, jerseyRegistry, "metric.jersey", jerseyRegistry.getClass());

            context.setAttribute(MetricsServlet.METRICS_REGISTRY, baseRegistry);
            context.setAttribute(HealthCheckServlet.HEALTH_CHECK_REGISTRY, new HealthCheckRegistry());
        }
    }

    @Test
    void testBootstrap() throws IOException, InterruptedException {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .defaults()
                .services(Services.builder()
                        .service(Service.builder("testRepository")
                                .enabled(true)
                                .clazz(TestRepository.class)
                                .runLevel(12))
                )
                .filters(Filters.builder()
                        .filter(Filter.builder("cors")
                                .clazz(ApplicationCORSServletFilter.class)
                                .pathSpec("/*")
                                .dispatches(EnumSet.of(DispatcherType.FORWARD, DispatcherType.REQUEST)))
                )
                .servlets(Servlets.builder()
                        .servlet(Servlet.builder("admin")
                                .enabled(true)
                                .clazz(AdminServlet.class)
                                .pathSpec("/admin/*")
                        )
                        .servlet(Servlet.builder("metrics")
                                .enabled(true)
                                .clazz(MetricsServlet.class)
                                .pathSpec("/admin/metrics/app/*"))
                )
                .servletContext(ServletContext.builder()
                        .initializer(MetricsServiceInitializer.class)
                )
                .resources(Resources.builder()
                        .resource(Resource.builder("greeting")
                                .clazz(EmbeddedApplicationTest.GreetingResource.class))
                        .resource(Resource.builder("metricResource")
                                .clazz(MetricsFeature.class)
                                .args(Args.builder()
                                        .arg(MetricRegistry.class, "metric.jersey")
                                )
                        )
                )
                .build();

        try (StrideApplication application = StrideApplication.create(configuration)) {
            log.trace("activate");
            application.activate();

            boolean isActive = application.getServiceLocator().getServiceHandle(TestRepository.class).isActive();
            assertFalse(isActive, "TestRepository found, should be null");

            log.trace("start");
            application.start();
            TestRepository testRepo = application.getServiceLocator().getService(TestRepository.class);
            assertNotNull(testRepo, "TestRepository not found");
            log.trace("port: {}", application.getLocalPort());

            HttpClient client = HttpClient.newHttpClient();
            {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(
                                        URI.create("http://localhost:" + application.getLocalPort() + "/ping"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                log.trace("resp: {}", response.statusCode());
            }
            {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(
                                        "http://localhost:" + application.getLocalPort() + "/greet/world?greeting=hello"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                log.trace("resp: {} => {}", response.statusCode(), response.body());
            }
        }
    }

    public static class TestRepository implements PreDestroy {

        private static final Logger log = LoggerFactory.getLogger(TestRepository.class);
        private final ApplicationConfiguration configuration;
        private final ServiceLocator serviceLocator;

        @Inject
        public TestRepository(ApplicationConfiguration configuration, ServiceLocator serviceLocator) {
            this.configuration = configuration;
            this.serviceLocator = serviceLocator;
            int l = serviceLocator.getService(RunLevelController.class)
                    .getCurrentRunLevel();
            log.info("Create: {}", l);
        }

        @Override
        public void preDestroy() {
            int l = serviceLocator.getService(RunLevelController.class)
                    .getCurrentRunLevel();
            log.warn("Destroy: {}", l);
        }
    }
}