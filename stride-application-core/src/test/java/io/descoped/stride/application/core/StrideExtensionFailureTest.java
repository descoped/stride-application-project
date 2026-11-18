package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import org.glassfish.hk2.api.PreDestroy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/**
 * What {@link StrideExtension} does when something goes wrong. Each case is a test class that fails on purpose;
 * it is run from here and its outcome is asserted. Run on their own, as an IDE does for a whole package, those
 * classes are disabled.
 */
class StrideExtensionFailureTest {

    private static final String ARMED = "io.descoped.stride.application.core.StrideExtensionFailureTest#armed";
    private static volatile boolean armed;

    static boolean armed() {
        return armed;
    }

    private static TestExecutionSummary run(Class<?> testClass) {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        armed = true;
        try {
            launcher.execute(request().selectors(selectClass(testClass)).build(), listener);
        } finally {
            armed = false;
        }
        return listener.getSummary();
    }

    // ---- an application that cannot start --------------------------------------------------------------------

    static final AtomicInteger STARTED = new AtomicInteger();
    static final AtomicInteger STOPPED = new AtomicInteger();

    public static class EarlyService implements PreDestroy {
        public EarlyService() {
            STARTED.incrementAndGet();
        }

        @Override
        public void preDestroy() {
            STOPPED.incrementAndGet();
        }
    }

    public static class BrokenService {
        public BrokenService() {
            throw new IllegalStateException("broken on purpose");
        }
    }

    @EnabledIf(ARMED)
    static class ApplicationThatCannotStart {
        @RegisterExtension
        static final StrideExtension stride = StrideExtension.builder()
                .configuration(() -> ApplicationConfiguration.builder().testDefaults()
                        .services(Services.builder()
                                .service(Service.builder("early").clazz(EarlyService.class).runLevel(3))
                                .service(Service.builder("broken").clazz(BrokenService.class).runLevel(5))))
                .build();

        @Test
        void neverRuns(TestClient client) {
            fail("the application did not start, so no test may run");
        }
    }

    @Test
    void aFailedStartFailsTheClassAndLeavesNothingRunning() {
        STARTED.set(0);
        STOPPED.set(0);

        TestExecutionSummary summary = run(ApplicationThatCannotStart.class);

        assertEquals(0, summary.getTestsStartedCount(), "no test runs without an application");
        assertEquals(1, summary.getFailures().size());
        var failure = summary.getFailures().getFirst();
        assertTrue(failure.getTestIdentifier().isContainer(), "the class fails, not a test");
        assertTrue(failure.getException().getMessage().contains("broken on purpose"), failure.getException().toString());
        assertEquals(1, STARTED.get());
        assertEquals(1, STOPPED.get(), "what had started before the failure is stopped");
    }

    // ---- a parameter asked for before the application of a test exists ---------------------------------------

    // The extension of the outer instance is known when the nested class is constructed, and has not started
    // the application of the test yet. (A constructor of the outer class is refused by JUnit itself: the
    // extension in its instance field is not registered before the instance exists.)
    @EnabledIf(ARMED)
    static class ClientInTheConstructorOfANestedClass {
        @RegisterExtension
        final StrideExtension stride = new StrideExtension();

        @Nested
        class Inner {
            Inner(TestClient client) {
            }

            @Test
            void neverRuns() {
                fail("the constructor cannot be given a client");
            }
        }
    }

    @Test
    void aParameterAskedForTooEarlySaysHowToGetIt() {
        TestExecutionSummary summary = run(ClientInTheConstructorOfANestedClass.class);

        assertEquals(1, summary.getFailures().size());
        Throwable failure = summary.getFailures().getFirst().getException();
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        assertInstanceOf(IllegalStateException.class, cause, failure.toString());
        assertTrue(cause.getMessage().startsWith("No application is running yet."), cause.getMessage());
        assertTrue(cause.getMessage().endsWith("use a static field to have it in constructors and @BeforeAll methods"),
                cause.getMessage());
    }

    // ---- a test that fails -----------------------------------------------------------------------------------

    static final AtomicReference<TestApplication> SEEN = new AtomicReference<>();

    @EnabledIf(ARMED)
    static class TestThatFails {
        @RegisterExtension
        static final StrideExtension stride = new StrideExtension();

        @Test
        void fails(TestApplication application, TestClient client) {
            client.get("/ping").expect200Ok();
            SEEN.set(application);
            fail("failing on purpose");
        }
    }

    @Test
    void theApplicationIsStoppedWhenATestFails() {
        SEEN.set(null);

        TestExecutionSummary summary = run(TestThatFails.class);

        assertEquals(1, summary.getTestsFailedCount());
        assertEquals("failing on purpose", summary.getFailures().getFirst().getException().getMessage());
        assertNotNull(SEEN.get(), "the test ran against a started application");
        assertFalse(SEEN.get().isRunning());
    }
}
