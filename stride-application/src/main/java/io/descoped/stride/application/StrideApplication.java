package io.descoped.stride.application;

import io.descoped.stride.application.core.StrideApplicationImpl;
import no.cantara.config.ApplicationProperties;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.hk2.api.ServiceLocator;

import java.util.Optional;

public interface StrideApplication extends AutoCloseable {

    void start();

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
}
