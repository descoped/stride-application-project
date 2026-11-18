package io.descoped.stride.application.api;

import io.descoped.stride.application.config.ApplicationConfiguration;
import org.glassfish.hk2.api.ServiceLocator;

public interface StrideApplication extends AutoCloseable {

    /**
     * Proceed to service run-level (before web server)
     */
    void activate();

    /**
     * Proceed to given run-level
     */
    void proceedTo(int runLevel);

    /**
     * Start application by enabling run-level max
     */
    void start();

    /**
     * Stop application by enabling run-level 0. Requests that are running get time to finish. A stopped
     * application cannot be started again: {@link #start()}, {@link #activate()} and {@link #proceedTo(int)} above
     * 0 then fail with an {@code IllegalStateException}. Stopping again does nothing.
     */
    void stop();

    boolean isRunning();

    boolean isCompleted();

    @Override
    void close();

    ServiceLocator getServiceLocator();

    /** The host the server listens on; {@code localhost} before the server is started and after it is stopped. */
    String getHost();

    /** The port the server listens on; {@code -1} before the server is started and after it is stopped. */
    int getLocalPort();

    /** The configured port, {@code 0} for a free one; {@code -1} before the server is started and after it is stopped. */
    int getPort();

    static StrideApplication create() {
        return StrideApplication.create(ApplicationConfiguration.builder().defaults().build());
    }

    static StrideApplication create(ApplicationConfiguration configuration) {
        return create(configuration, Components.none());
    }

    /**
     * Creates an application from a configuration and the ready-made objects the application has wired itself.
     */
    static StrideApplication create(ApplicationConfiguration configuration, Components components) {
        ApplicationConfigurator configurator = new ApplicationConfigurator();
        return configurator.configure(configuration, components);
    }
}
