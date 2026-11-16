package io.descoped.stride.application.core;

import jakarta.servlet.ServletContext;
import org.glassfish.hk2.api.ServiceLocator;

public interface ServletContextInitializer {

    void initialize(ServiceLocator locator, ServletContext context);

}
