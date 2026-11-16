package io.descoped.stride.application.core;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In an instance field: every test gets a new application with fresh objects, and the application of a test is
 * stopped before the next one starts.
 */
class StrideExtensionPerTestTest {

    @Path("/count")
    public static class CountResource {
        private final AtomicInteger count = new AtomicInteger();

        @GET
        public int next() {
            return count.incrementAndGet();
        }
    }

    static final List<TestApplication> EARLIER = new CopyOnWriteArrayList<>();

    @RegisterExtension
    final StrideExtension stride = StrideExtension.builder()
            .components(() -> Components.builder().resource(new CountResource()).build())
            .build();

    @RepeatedTest(3)
    void everyTestHasItsOwnApplication(TestApplication application, TestClient client) {
        for (TestApplication earlier : EARLIER) {
            assertFalse(earlier.isRunning(), "the application of an earlier test was stopped");
        }
        assertFalse(EARLIER.contains(application));
        assertTrue(application.isRunning());
        assertEquals("1", client.get("/count").expect200Ok().body(), "the resource is new, so it counts from one");
        assertEquals("2", client.get("/count").expect200Ok().body());
        EARLIER.add(application);
    }

    @AfterAll
    static void everyApplicationIsStopped() {
        assertEquals(3, EARLIER.size());
        for (TestApplication application : EARLIER) {
            assertFalse(application.isRunning());
        }
    }
}
