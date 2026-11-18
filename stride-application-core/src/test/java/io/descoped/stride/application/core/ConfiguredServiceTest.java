package io.descoped.stride.application.core;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A service class named in the configuration is created by Stride at its run level, or startup says why not.
 */
class ConfiguredServiceTest {

    static final AtomicInteger PLAIN = new AtomicInteger();
    static final AtomicInteger NAMED = new AtomicInteger();

    public static class PlainService {
        public PlainService() {
            PLAIN.incrementAndGet();
        }
    }

    @Named("named")
    public static class NamedService {
        public NamedService() {
            NAMED.incrementAndGet();
        }
    }

    @Singleton
    public static class SingletonService {
    }

    @org.jvnet.hk2.annotations.Service
    public static class Hk2Service {
    }

    private static ApplicationConfiguration configuration(String name, Class<?> serviceClass) {
        return ApplicationConfiguration.builder()
                .testDefaults()
                .services(Services.builder().service(Service.builder(name).clazz(serviceClass).runLevel(5)))
                .build();
    }

    @Test
    void plainAndNamedClassesStartOnceAtTheirRunLevel() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .services(Services.builder()
                        .service(Service.builder("plain").clazz(PlainService.class).runLevel(5))
                        .service(Service.builder("named").clazz(NamedService.class).runLevel(5)))
                .build();
        PLAIN.set(0);
        NAMED.set(0);

        try (StrideApplication application = StrideApplication.create(configuration)) {
            application.start();

            assertEquals(1, PLAIN.get());
            assertEquals(1, NAMED.get());
            assertNotNull(application.getServiceLocator().getServiceHandle(NamedService.class, "named"));
        }
    }

    @Test
    void aClassWithItsOwnScopeIsRejectedNamingTheKey() {
        ApplicationConfiguration configuration = configuration("cache", SingletonService.class);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> StrideApplication.create(configuration));

        assertTrue(failure.getMessage().contains("services.cache.config.class"), failure.getMessage());
        assertTrue(failure.getMessage().contains(SingletonService.class.getName()), failure.getMessage());
    }

    @Test
    void anHk2ServiceClassIsRejectedInsteadOfBeingLeftOut() {
        ApplicationConfiguration configuration = configuration("discovered", Hk2Service.class);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> StrideApplication.create(configuration));

        assertTrue(failure.getMessage().contains("services.discovered.config.class"), failure.getMessage());
    }

    @Test
    void aServiceDeclaredByItsClassAloneIsCreated() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .services(Services.builder().service(Service.builder(PlainService.class).runLevel(5)))
                .build();
        PLAIN.set(0);

        try (StrideApplication application = StrideApplication.create(configuration)) {
            application.start();

            assertEquals(1, PLAIN.get());
        }
    }

    @Test
    void aServiceWithoutARunLevelIsCreatedWhenItIsFirstAskedFor() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .services(Services.builder().service(Service.builder("plain").clazz(PlainService.class)))
                .build();
        PLAIN.set(0);

        try (StrideApplication application = StrideApplication.create(configuration)) {
            application.start();
            assertEquals(0, PLAIN.get(), "no run level starts it");

            assertNotNull(application.getServiceLocator().getService(PlainService.class));
            application.getServiceLocator().getService(PlainService.class);

            assertEquals(1, PLAIN.get(), "created once, on demand");
        }
    }
}
