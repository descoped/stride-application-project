package io.descoped.stride.application.api;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.spi.ApplicationInitializer;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

class ApplicationConfigurator {

    StrideApplication configure(ApplicationConfiguration configuration, Components components) {
        List<ApplicationInitializer> initializers = ServiceLoader.load(ApplicationInitializer.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        if (initializers.isEmpty()) {
            throw new IllegalStateException("No Stride runtime on the class path: add stride-application-core, or "
                    + "another provider of " + ApplicationInitializer.class.getName());
        }
        if (initializers.size() > 1) {
            // which one comes first depends on the order of the class path: not a choice to make silently
            throw new IllegalStateException("More than one Stride runtime on the class path, and only one can create "
                    + "the application: " + initializers.stream().map(i -> i.getClass().getName()).sorted()
                    .collect(Collectors.joining(", ")));
        }
        ApplicationInitializer initializer = initializers.getFirst();
        return initializer.initialize(configuration, components);
    }
}
