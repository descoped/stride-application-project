package io.descoped.stride.application.api;

import io.descoped.stride.application.config.api.ApplicationConfiguration;
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
     * Stop application by enabling run-level 0
     */
    void stop();

    boolean isRunning();

    boolean isCompleted();

    @Override
    void close();

    ServiceLocator getServiceLocator();

    String getHost();

    int getLocalPort();

    int getPort();

    static StrideApplication create() {
        return StrideApplication.create(ApplicationConfiguration.builder().build());
    }

    static StrideApplication create(ApplicationConfiguration configuration) {
        ApplicationConfigurator configurator = new ApplicationConfigurator();
        return configurator.configure(configuration);
    }
}
