package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import io.descoped.stride.application.test.server.TestClientException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An application can be brought up in steps: its own services first, the web server and the listener after.
 * Services start by run level: application services up to 9, the web server at 10, the listener at 11.
 */
class LifecycleStepsTest {

    /** An application service, below the web server. */
    public static class Early {
    }

    /** A service that needs the listener to be up. */
    public static class Late {
    }

    private static TestApplication application() {
        return new TestApplication.Builder()
                .configuration(ApplicationConfiguration.builder().testDefaults()
                        .services(Services.builder()
                                .service(Service.builder("early").clazz(Early.class).runLevel(5))
                                .service(Service.builder("late").clazz(Late.class).runLevel(12))))
                .build();
    }

    private static boolean active(TestApplication application, Class<?> service) {
        return application.getServiceLocator().getServiceHandle(service).isActive();
    }

    @Test
    void activateStartsTheApplicationsServicesAndNotTheServer() {
        try (TestApplication application = application()) {
            application.activate();

            assertTrue(active(application, Early.class));
            assertFalse(active(application, Late.class));
            assertEquals(-1, application.getLocalPort(), "nothing listens yet");
            assertEquals(-1, application.getPort());
            assertTrue(application.isRunning());
            assertFalse(application.isCompleted());
        }
    }

    @Test
    void startAfterActivateBringsUpTheServerAndTheServicesAboveIt() {
        try (TestApplication application = application()) {
            application.activate();
            application.start();

            assertTrue(active(application, Late.class));
            assertEquals("localhost", application.getHost());
            assertTrue(application.getLocalPort() > 0);
            TestClient.create(application).get("/ping").expect200Ok();
        }
    }

    @Test
    void proceedingToARunLevelStartsTheServicesUpToItAndNoFurther() {
        try (TestApplication application = application()) {
            application.proceedTo(4);
            assertFalse(active(application, Early.class));

            application.proceedTo(5);
            assertTrue(active(application, Early.class));
            assertFalse(active(application, Late.class));
        }
    }

    @Test
    void proceedingToZeroStopsARunningApplication() {
        try (TestApplication application = application()) {
            application.start();
            int port = application.getLocalPort();
            TestClient client = TestClient.create((uri) -> "http://localhost:" + port + uri, Duration.ofSeconds(5));
            client.get("/ping").expect200Ok();

            application.proceedTo(0);

            assertTrue(application.isCompleted());
            assertFalse(application.isRunning());
            assertThrows(TestClientException.class, () -> client.get("/ping"));
        }
    }
}
