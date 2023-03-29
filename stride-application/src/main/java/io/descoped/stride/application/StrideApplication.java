package io.descoped.stride.application;

import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.config.Servlets;
import io.descoped.stride.application.core.ApplicationBuilder;
import io.descoped.stride.application.core.StrideApplicationImpl;
import no.cantara.config.ApplicationProperties;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.hk2.api.ServiceLocator;

import java.util.Optional;

public interface StrideApplication extends AutoCloseable {

    /**
     * Proceed to service run-level (before web server)
     */
    void proceedToServiceRunLevel();

    /**
     * Proceed to given run-level
     */
    void proceedToRunLevel(int runLevel);

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

    static StrideApplication create(ApplicationProperties configuration) {
        return new StrideApplicationImpl(configuration);
    }

    static StrideApplication.Builder builder() {
        return new ApplicationBuilder();
    }

    interface Builder {
        Builder configuration(ApplicationProperties applicationProperties);

        Builder services(Services.Builder servicesBuilder);

        Builder filters(Filters.Builder filtersBuilder);

        Builder servlets(Servlets.Builder servletsBuilder);

        StrideApplication build();
    }
}
