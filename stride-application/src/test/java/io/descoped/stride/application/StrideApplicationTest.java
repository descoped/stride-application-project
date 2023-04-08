package io.descoped.stride.application;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Deployment;
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.core.ServiceLocatorUtils;
import io.descoped.stride.application.cors.ApplicationCORSServletFilter;
import jakarta.inject.Inject;
import jakarta.servlet.DispatcherType;
import org.glassfish.hk2.api.PreDestroy;
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
        Deployment deployment = Deployment.builder()
                .services(Services.builder()
                        .service(Service.builder("testRepository")
                                .clazz(TestRepository.class)
                                .runLevel(12))

                )
                .filters(Filters.builder()
                        .filter(Filter.builder("cors-filter")
                                .clazz(ApplicationCORSServletFilter.class)
                                .pathSpec("/*")
                                .dispatches(EnumSet.allOf(DispatcherType.class)))
                )
                .resources(Resources.builder()
                        .resource(Resource.builder("greeting")
                                .clazz(EmbeddedApplicationTest.GreetingResource.class)))
                .build();

        try (StrideApplication application = StrideApplication.create(deployment)) {
            log.trace("proceedTo");
            application.activate();

            boolean isAct = application.getServiceLocator().getServiceHandle(TestRepository.class)
                    .isActive();
            assertFalse(isAct, "TestRepository found, should be null");

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

        @Inject
        public TestRepository(ApplicationConfiguration configuration) {
            int l = ServiceLocatorUtils.instance().getService(RunLevelController.class)
                    .getCurrentRunLevel();
            log.info("Create: {}", l);
        }

        @Override
        public void preDestroy() {
            int l = ServiceLocatorUtils.instance().getService(RunLevelController.class)
                    .getCurrentRunLevel();
            log.warn("Destroy: {}", l);
        }
    }

}