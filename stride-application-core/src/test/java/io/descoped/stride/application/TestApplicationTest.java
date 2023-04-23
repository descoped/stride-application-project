package io.descoped.stride.application;

import io.descoped.stride.application.config.api.ApplicationConfiguration;
import io.descoped.stride.application.config.api.Resource;
import io.descoped.stride.application.config.api.Resources;
import io.descoped.stride.application.test.TestApplication;
import org.junit.jupiter.api.Test;

public class TestApplicationTest {

    @Test
    void testApplication() {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .resources(Resources.builder()
                        .resource(Resource.builder("greeting")
                                .clazz(EmbeddedApplicationTest.GreetingResource.class)
                        )
                );

        TestApplication application = new TestApplication.Builder()
                .configuration(configuration)
                .build();

        application.start();
        application.stop();
    }
}
