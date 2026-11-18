package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestApplicationTest {

    private TestApplication application;

    @BeforeEach
    void setUp() {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .resources(Resources.builder()
                        .resource(Resource.builder("greeting")
                                .clazz(GreetingResource.class)
                        )
                );

        application = new TestApplication.Builder().configuration(configuration).build();
        application.start();
    }

    @AfterEach
    void tearDown() {
        application.stop();
    }

    @Test
    void testApplication() {
        assertTrue(application.isRunning());
        assertTrue(application.getLocalPort() > 0, "the server listens on a port of its own choosing");

        try (TestClient client = TestClient.create(application)) {
            assertEquals("{\"name\":\"world\",\"greeting\":\"hello\"}",
                    client.get("/greet/world?greeting=hello").expect200Ok().body());
        }
    }
}
