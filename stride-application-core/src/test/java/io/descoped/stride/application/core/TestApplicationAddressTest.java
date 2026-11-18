package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a test's requests go: the address the application listens on, under the context path it runs under.
 */
class TestApplicationAddressTest {

    @Test
    void aTestRunsAtTheRootWhateverTheConfigurationSays() {
        try (TestApplication application = new TestApplication.Builder()
                .configuration(ApplicationConfiguration.builder().testDefaults()
                        .overrideProperty("services.jetty.server.config.context-path", "/configured"))
                .build()) {
            application.start();

            assertEquals("http://localhost:" + application.getLocalPort() + "/ping", application.testURL("/ping"));
            TestClient.create(application).get("/ping").expect200Ok();
        }
    }

    @Test
    void aTestThatAsksForAContextPathSendsItsRequestsUnderIt() {
        try (TestApplication application = new TestApplication.Builder().contextPath("/api").build()) {
            application.start();

            assertEquals("http://localhost:" + application.getLocalPort() + "/api/ping", application.testURL("/ping"));
            TestClient.create(application).get("/ping").expect200Ok();
        }
    }

    @Test
    void aTestCanKeepTheContextPathOfTheConfiguration() {
        try (TestApplication application = new TestApplication.Builder()
                .configuration(ApplicationConfiguration.builder().testDefaults()
                        .overrideProperty("services.jetty.server.config.context-path", "/configured"))
                .contextPath(null)
                .build()) {
            application.start();

            assertTrue(application.testURL("/ping").endsWith("/configured/ping"), application.testURL("/ping"));
            TestClient.create(application).get("/ping").expect200Ok();
        }
    }
}
