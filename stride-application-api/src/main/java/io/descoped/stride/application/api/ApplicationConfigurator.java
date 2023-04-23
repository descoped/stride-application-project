package io.descoped.stride.application.api;

import io.descoped.stride.application.config.api.ApplicationConfiguration;
import io.descoped.stride.application.spi.ApplicationInitializer;

import java.util.Optional;
import java.util.ServiceLoader;

class ApplicationConfigurator {

    StrideApplication configure(ApplicationConfiguration configuration) {
        ServiceLoader<ApplicationInitializer> serviceLoader = ServiceLoader.load(ApplicationInitializer.class);
        Optional<ApplicationInitializer> initializer = serviceLoader.findFirst();
        if (initializer.isEmpty()) {
            throw new RuntimeException("Missing SPI for " + ApplicationInitializer.class.getName());
        }
        return initializer.get().initialize(configuration);
    }
}
