package io.descoped.stride.application;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.api.ApplicationConfiguration;
import io.descoped.stride.application.core.ApplicationInitialization;
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
