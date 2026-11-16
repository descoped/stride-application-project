package io.descoped.stride.application.core;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import io.descoped.stride.application.test.server.TestUriResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registered on the class: one application with the test defaults serves every test, and is there from
 * {@code @BeforeAll} on.
 */
@ExtendWith(StrideExtension.class)
class StrideExtensionDefaultsTest {

    static TestApplication startedBeforeAll;

    private final TestApplication fromConstructor;

    StrideExtensionDefaultsTest(TestApplication application) {
        this.fromConstructor = application;
    }

    @BeforeAll
    static void applicationIsRunningBeforeAll(TestApplication application, TestClient client) {
        assertTrue(application.isRunning());
        assertTrue(application.getLocalPort() > 0, "a free port was picked");
        client.get("/ping").expect200Ok();
        startedBeforeAll = application;
    }

    @Test
    void everyTestGetsTheApplicationOfTheClass(TestApplication application, StrideApplication sameAsStride,
                                               TestUriResolver sameAsResolver) {
        assertSame(startedBeforeAll, application);
        assertSame(startedBeforeAll, fromConstructor);
        assertSame(startedBeforeAll, sameAsStride);
        assertSame(startedBeforeAll, sameAsResolver);
    }

    @Nested
    class InANestedClass {
        @Test
        void theApplicationOfTheOuterClassIsShared(TestApplication application, TestClient client) {
            assertSame(startedBeforeAll, application);
            client.get("/ping").expect200Ok();
        }
    }

    @Test
    void theClientCallsTheRunningApplication(TestClient client, TestApplication application) {
        assertNotNull(client);
        assertSame(application, client.getTestUriResolver());
        assertEquals(TestClient.DEFAULT_TIMEOUT, client.timeout());
        client.get("/ping").expect200Ok();
    }
}
