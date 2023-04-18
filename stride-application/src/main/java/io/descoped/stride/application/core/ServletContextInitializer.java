package io.descoped.stride.application.core;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.glassfish.hk2.api.ServiceLocator;

public interface ServletContextInitializer {

    void initialize(ServiceLocator locator, ContextHandler.Context context);

}
