package io.descoped.stride.application.core;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import io.descoped.stride.application.test.server.TestClientException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In a static field: the application gets the test's configuration and ready-made objects, and the client its
 * timeout.
 */
class StrideExtensionConfiguredTest {

    @Path("/greeting")
    public static class GreetingResource {
        private final String greeting;
        private final CountDownLatch release;

        GreetingResource(String greeting, CountDownLatch release) {
            this.greeting = greeting;
            this.release = release;
        }

        @GET
        public String greet() {
            return greeting;
        }

        @GET
        @Path("never")
        public String never() throws InterruptedException {
            release.await(); // answers only when the test lets it
            return "late";
        }
    }

    static final CountDownLatch RELEASE = new CountDownLatch(1);

    @RegisterExtension
    static final StrideExtension stride = StrideExtension.builder()
            .configuration(() -> ApplicationConfiguration.builder().testDefaults()
                    .overrideProperty("application.alias", "configured"))
            .components(() -> Components.builder().resource(new GreetingResource("hello", RELEASE)).build())
            .timeout(Duration.ofMillis(500))
            .build();

    @Test
    void theApplicationHasTheGivenConfigurationAndObjects(TestClient client, TestApplication application) {
        ApplicationConfiguration configuration = application.getServiceLocator().getService(ApplicationConfiguration.class);
        assertEquals("configured", configuration.requireString("application.alias"));
        assertEquals("hello", client.get("/greeting").expect200Ok().body());
        assertEquals(Duration.ofMillis(500), client.timeout());
    }

    @Test
    void aRequestThatIsNotAnsweredFailsAfterTheTimeoutInsteadOfHanging(TestClient client) {
        long started = System.nanoTime();
        try {
            TestClientException failure = assertThrows(TestClientException.class, () -> client.get("/greeting/never"));

            Duration waited = Duration.ofNanos(System.nanoTime() - started);
            assertInstanceOf(HttpTimeoutException.class, failure.getCause());
            assertTrue(failure.getMessage().startsWith("GET /greeting/never failed"), failure.getMessage());
            assertTrue(waited.compareTo(Duration.ofMillis(400)) >= 0 && waited.compareTo(Duration.ofSeconds(10)) < 0,
                    "waited " + waited);
        } finally {
            RELEASE.countDown();
        }
    }
}
