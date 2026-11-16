package io.descoped.stride.application.core;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeIntegrationTest {
    public static class LateService { }

    @Test void startsAllServiceLevelsWithSmallWorkerPoolAndHonorsContextPath() throws Exception {
        var config = ApplicationConfiguration.builder().testDefaults()
                .overrideProperty("services.hk2.threadcount", "2")
                .overrideProperty("services.jetty.server.config.host", "127.0.0.1")
                .overrideProperty("services.jetty.server.config.context-path", "/api")
                .overrideProperty("services.jetty.server.config.port", "0")
                .resources(Resources.builder().resource(Resource.builder("greeting").enabled(true).clazz(GreetingResource.class)))
                .services(Services.builder().service(Service.builder("late").enabled(true).clazz(LateService.class).runLevel(25)))
                .build();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
             var application = StrideApplication.create(config)) {
            application.activate();
            assertEquals(-1, application.getLocalPort(), "activation must not accept HTTP traffic");
            assertFalse(application.getServiceLocator().getServiceHandle(LateService.class).isActive());
            application.start();
            assertTrue(application.isRunning());
            assertTrue(application.getServiceLocator().getServiceHandle(LateService.class).isActive());
            assertEquals("127.0.0.1", application.getHost());
            assertTrue(application.getLocalPort() > 0);
            String base = "http://127.0.0.1:" + application.getLocalPort();
            var ping = client.send(HttpRequest.newBuilder(URI.create(base + "/api/ping")).timeout(Duration.ofSeconds(3)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ping.statusCode());
            var greet = client.send(HttpRequest.newBuilder(URI.create(base + "/api/greet/world?greeting=hello")).timeout(Duration.ofSeconds(3)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, greet.statusCode());
            assertTrue(greet.body().contains("hello"));
            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/ping")).timeout(Duration.ofSeconds(3)).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            application.stop();
            application.close();
            assertTrue(application.isCompleted());
        }
    }
}
