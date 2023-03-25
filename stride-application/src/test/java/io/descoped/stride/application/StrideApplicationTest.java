package io.descoped.stride.application;

import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.core.ServiceLocatorUtils;
import no.cantara.config.ApplicationProperties;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.ServiceLocator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StrideApplicationTest {

    private static final Logger log = LoggerFactory.getLogger(StrideApplicationTest.class);

    @Test
    void testBootstrap() throws IOException, InterruptedException {
        ApplicationProperties properties = ApplicationProperties.builder()
                .classpathPropertiesFile("application-defaults.properties")
                .testDefaults()
                .build();

        Services services = Services.builder()
                .service(Services.serviceBuilder()
                        .name("testRepository")
                        .clazz(TestRepository.class))
                .build();

        ServiceLocator serviceLocator = ServiceLocatorUtils.instance();
        DynamicConfigurationService dcs = serviceLocator.getService(DynamicConfigurationService.class);


        try (StrideApplication application = StrideApplication.create(properties)) {
            log.trace("act");
            application.proceedToServiceRunLevel();
            assertEquals(serviceLocator.getName(), application.getServiceLocator().getName());
            application.start();
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

    static class TestRepository {
        public TestRepository() {
        }
    }

}