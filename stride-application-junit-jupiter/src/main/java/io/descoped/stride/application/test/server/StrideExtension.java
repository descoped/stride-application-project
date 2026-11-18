package io.descoped.stride.application.test.server;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Starts a real Stride application for a test class and hands the tests the application and a client for it.
 * <p>
 * With the defaults of {@code ApplicationConfiguration.builder().testDefaults()}:
 * <pre>{@code
 * @ExtendWith(StrideExtension.class)
 * class PingTest {
 *     @Test
 *     void answers(TestClient client) {
 *         client.get("/ping").expect200Ok();
 *     }
 * }
 * }</pre>
 * With the application's own configuration and ready-made objects:
 * <pre>{@code
 * class OrderTest {
 *     @RegisterExtension
 *     static final StrideExtension stride = StrideExtension.builder()
 *             .configuration(() -> ApplicationConfiguration.builder().testDefaults())
 *             .components(() -> Components.builder().resource(new OrderResource(new FakeOrders())).build())
 *             .build();
 *
 *     @Test
 *     void listsOrders(TestClient client) {
 *         client.get("/orders").expect200Ok();
 *     }
 * }
 * }</pre>
 * Registered on the class or in a static field, one application serves every test of the class and of its
 * {@code @Nested} classes. Registered in an
 * instance field, every test gets a new application. Either way the application listens on a free port, and it is
 * stopped and the client closed when the class or the test is done, also when a test fails.
 * <p>
 * Test methods, constructors and lifecycle methods may declare parameters of type {@link TestClient},
 * {@link TestApplication}, {@link StrideApplication} and {@link TestUriResolver}.
 */
public final class StrideExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private final Supplier<ApplicationConfiguration.Builder> configuration;
    private final Function<ApplicationConfiguration, Components> components;
    private final Duration timeout;
    // one namespace for each extension, so that a running application belongs to the extension that started it
    private final Namespace namespace = Namespace.create(StrideExtension.class, this);

    /** An application with the test defaults and nothing handed in. */
    public StrideExtension() {
        this(builder());
    }

    private StrideExtension(Builder builder) {
        this.configuration = builder.configuration;
        this.components = builder.components;
        this.timeout = builder.timeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The application and its client; the store closes it when its class or test is done. */
    private record Running(TestApplication application, TestClient client) implements AutoCloseable {
        @Override
        public void close() {
            try {
                client.close();
            } finally {
                application.close();
            }
        }
    }

    private Running start() {
        TestApplication application = new TestApplication.Builder()
                .configuration(configuration.get())
                .components(components)
                .build();
        try {
            application.start();
            return new Running(application, TestClient.create(application, timeout));
        } catch (RuntimeException | Error e) {
            try {
                application.close();
            } catch (RuntimeException closing) {
                e.addSuppressed(closing);
            }
            throw e;
        }
    }

    private Running running(ExtensionContext context) {
        return context.getStore(namespace).get(Running.class, Running.class);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (running(context) == null) { // a @Nested class shares the application of the class around it
            context.getStore(namespace).put(Running.class, start());
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (running(context) == null) { // not started for the class: this extension is registered for each test
            context.getStore(namespace).put(Running.class, start());
        }
    }

    // The store would close a test's application too, but only after the test's other resources; stopping here
    // makes the order plain: the application of a test is down before the next test starts.
    @Override
    public void afterEach(ExtensionContext context) {
        Object own = context.getStore(namespace).remove(Running.class);
        if (own instanceof Running running) {
            running.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Class<?> type = parameterContext.getParameter().getType();
        return type == TestClient.class || type == TestApplication.class || type == StrideApplication.class
                || type == TestUriResolver.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Running running = running(extensionContext);
        if (running == null) {
            throw new IllegalStateException("No application is running yet. A StrideExtension in an instance field "
                    + "starts the application for each test, after the test class is constructed and after "
                    + "@BeforeAll; use a static field to have it in constructors and @BeforeAll methods");
        }
        return parameterContext.getParameter().getType() == TestClient.class ? running.client() : running.application();
    }

    public static final class Builder {

        private Supplier<ApplicationConfiguration.Builder> configuration = () -> ApplicationConfiguration.builder().testDefaults();
        private Function<ApplicationConfiguration, Components> components = _ -> Components.none();
        private Duration timeout = TestClient.DEFAULT_TIMEOUT;

        private Builder() {
        }

        /**
         * The configuration of the application. The supplier is called for every start, so each application gets
         * its own builder. Host, port and context path are set by the test library: localhost and a free port.
         */
        public Builder configuration(Supplier<ApplicationConfiguration.Builder> configuration) {
            this.configuration = Objects.requireNonNull(configuration, "configuration");
            return this;
        }

        /**
         * Ready-made objects for the application under test, for example a resource wired to a fake collaborator.
         * The supplier is called for every start, so an application for each test gets fresh objects.
         */
        public Builder components(Supplier<Components> components) {
            Objects.requireNonNull(components, "components");
            this.components = _ -> components.get();
            return this;
        }

        /**
         * Ready-made objects wired from the configuration the application is started with: the way to run the
         * application's own wiring in a test, for example {@code .components(MyApplication::components)}. Called
         * for every start.
         */
        public Builder components(Function<ApplicationConfiguration, Components> components) {
            this.components = Objects.requireNonNull(components, "components");
            return this;
        }

        /**
         * How long the client waits for a connection, and for a request up to its response headers;
         * {@link TestClient#DEFAULT_TIMEOUT} if not set.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = TestClient.requirePositive(timeout);
            return this;
        }

        public StrideExtension build() {
            return new StrideExtension(this);
        }
    }
}
