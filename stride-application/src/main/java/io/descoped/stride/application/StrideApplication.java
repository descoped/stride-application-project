package io.descoped.stride.application;

import io.descoped.stride.application.config.Deployment;
import io.descoped.stride.application.core.ApplicationInitialization;
import io.descoped.stride.application.core.StrideApplicationImpl;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.hk2.api.ServiceLocator;

import java.util.Optional;

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

    @Override
    void close();

    ServiceLocator getServiceLocator();

    default String getHost() {
        Optional<ServerConnector> connector = ((StrideApplicationImpl) this).getConnector();
        return connector.map(ServerConnector::getHost).orElse("localhost");
    }

    default int getLocalPort() {
        Optional<ServerConnector> connector = ((StrideApplicationImpl) this).getConnector();
        return connector.map(ServerConnector::getLocalPort).orElse(-1);
    }

    default int getPort() {
        Optional<ServerConnector> connector = ((StrideApplicationImpl) this).getConnector();
        return connector.map(ServerConnector::getPort).orElse(-1);
    }

    static StrideApplication create(Deployment deployment) {
        ApplicationInitialization initialization = new ApplicationInitialization(deployment);
        return initialization.initialize();
    }
}
