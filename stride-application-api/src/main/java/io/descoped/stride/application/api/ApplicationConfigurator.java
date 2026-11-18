package io.descoped.stride.application.api;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.spi.ApplicationInitializer;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

class ApplicationConfigurator {

    StrideApplication configure(ApplicationConfiguration configuration, Components components) {
        // providers, not instances: a runtime is created only when it is the one that will be used
        List<ServiceLoader.Provider<ApplicationInitializer>> runtimes = ServiceLoader.load(ApplicationInitializer.class)
                .stream().toList();
        if (runtimes.isEmpty()) {
            throw new IllegalStateException("No Stride runtime on the class path: add stride-application-core, or "
                    + "another provider of " + ApplicationInitializer.class.getName());
        }
        if (runtimes.size() > 1) {
            // which one comes first depends on the order of the class path: not a choice to make silently
            throw new IllegalStateException("More than one Stride runtime on the class path, and only one can create "
                    + "the application: " + runtimes.stream().map(runtime -> runtime.type().getName()).sorted()
                    .collect(Collectors.joining(", ")));
        }
        ApplicationInitializer initializer = runtimes.getFirst().get();
        return initializer.initialize(configuration, components);
    }
}
