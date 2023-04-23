package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.test.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    }
}
