package io.descoped.stride.sample;

import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The application as it runs in production, started on a free port: same configuration, same wiring, real requests.
 */
class GreetingApplicationTest {

    // The application's own wiring gets the configuration the server is started with, override included.
    @RegisterExtension
    static final StrideExtension stride = StrideExtension.builder()
            .configuration(() -> GreetingApplication.configuration().overrideProperty("greeting.salutation", "Hei"))
            .components(GreetingApplication::components)
            .build();

    @Test
    void greetsByNameWithTheConfiguredSalutation(TestClient client) {
        var response = client.get("/greetings/Ada").expect200Ok().response();

        assertEquals("{\"message\":\"Hei, Ada!\"}", response.body());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void anUnknownPathIsNotFound(TestClient client) {
        client.get("/farewells/Ada").expect404NotFound();
    }

    @Test
    void theServerAnswersItsHealthCheck(TestClient client) {
        client.get("/ping").expect200Ok();
    }
}
