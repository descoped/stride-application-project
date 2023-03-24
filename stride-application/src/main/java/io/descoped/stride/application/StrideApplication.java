package io.descoped.stride.application;

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

    Optional<ServerConnector> getConnector();

    default String getHost() {
        Optional<ServerConnector> connector = getConnector();
        return connector.map(ServerConnector::getHost).orElse("localhost");
    }

    default int getLocalPort() {
        Optional<ServerConnector> connector = getConnector();
        return connector.map(ServerConnector::getLocalPort).orElse(-1);
    }

    default int getPort() {
        Optional<ServerConnector> connector = getConnector();
        return connector.map(ServerConnector::getPort).orElse(-1);
    }

    static StrideApplication create(ApplicationProperties configuration) {
        return new StrideApplicationImpl(configuration);
    }
}
