package io.descoped.stride.sample;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The application as it runs in production, started on a free port: same configuration, same wiring, real requests.
 */
class GreetingApplicationTest {

    static ApplicationConfiguration.Builder configuration() {
        return GreetingApplication.configuration().overrideProperty("greeting.salutation", "Hei");
    }

    @RegisterExtension
    static final StrideExtension stride = StrideExtension.builder()
            .configuration(GreetingApplicationTest::configuration)
            .components(() -> GreetingApplication.components(configuration().build()))
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
