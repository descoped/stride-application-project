package io.descoped.stride.application;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.config.Servlet;
import io.descoped.stride.application.config.ServletContext;
import io.descoped.stride.application.config.Servlets;
import io.descoped.stride.application.cors.ApplicationCORSServletFilter;
import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.HealthCheckServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import jakarta.inject.Inject;
import jakarta.servlet.DispatcherType;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevelController;
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

    @Test
    void testBootstrap() throws IOException, InterruptedException {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .defaults()
                .services(Services.builder()
                        .service(Service.builder("metrics.base")
                                .enabled(true)
                                .clazz(MetricRegistry.class))
                        .service(Service.builder(HealthCheckRegistry.class.getName())
                                .enabled(true)
                                .clazz(HealthCheckRegistry.class))
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
                                .context(ServletContext.builder()
                                        .bind(MetricsServlet.METRICS_REGISTRY, "metrics.base")
                                        .bind(HealthCheckServlet.HEALTH_CHECK_REGISTRY, HealthCheckRegistry.class.getName())))
                        .servlet(Servlet.builder("metrics")
                                .enabled(true)
                                .clazz(MetricsServlet.class)
                                .pathSpec("/admin/metrics/app/*")
                                .context(ServletContext.builder()
                                        .bind(MetricsServlet.METRICS_REGISTRY, "metrics.base")))
                )
                .resources(Resources.builder()
                        .resource(Resource.builder("greeting")
                                .clazz(EmbeddedApplicationTest.GreetingResource.class)))
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