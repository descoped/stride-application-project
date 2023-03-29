package io.descoped.stride.application;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.core.ServiceLocatorUtils;
import jakarta.inject.Inject;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StrideApplicationTest {

    private static final Logger log = LoggerFactory.getLogger(StrideApplicationTest.class);

    @Test
    void testBootstrap() throws IOException, InterruptedException {
        StrideApplication.Builder builder = StrideApplication.builder()
                .services(Services.builder()
                        .service(Services.serviceBuilder()
                                .name("testRepository")
                                .clazz(TestRepository.class)
                                .runLevel(12))
                );

        try (StrideApplication application = builder.build()) {
            log.trace("proceedTo");
            application.activate();

            boolean isAct = application.getServiceLocator().getServiceHandle(TestRepository.class).isActive();
            assertFalse(isAct, "TestRepository found, should be null");

            log.trace("start");
            application.start();
            TestRepository testRepo = application.getServiceLocator().getService(TestRepository.class);
            assertNotNull(testRepo, "TestRepository not found");
            log.trace("port: {}", application.getLocalPort());

            HttpClient client = HttpClient.newHttpClient();
            {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + application.getLocalPort() + "/ping"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString()
                );
                log.trace("resp: {}", response.statusCode());
            }
            {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + application.getLocalPort() + "/greet/world?greeting=hello"))
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
            int l = ServiceLocatorUtils.instance().getService(RunLevelController.class).getCurrentRunLevel();
            log.info("----------> {} -- {}", l, configuration.json().toPrettyString());
        }

        @Override
        public void preDestroy() {
            int l = ServiceLocatorUtils.instance().getService(RunLevelController.class).getCurrentRunLevel();
            log.error("==================================================================================== CLOSE - {}", l);
        }
    }

}