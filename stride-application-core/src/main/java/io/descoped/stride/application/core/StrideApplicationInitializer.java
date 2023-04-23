package io.descoped.stride.application.core;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.spi.ApplicationInitializer;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;

public class StrideApplicationInitializer implements ApplicationInitializer {

    @Override
    public StrideApplication initialize(ApplicationConfiguration configuration) {
        ServiceLocator serviceLocator = ServiceLocatorFactory.getInstance().create(null);
        ApplicationInitialization initialization = new ApplicationInitialization(configuration, serviceLocator);
        return initialization.initialize();
    }
}
