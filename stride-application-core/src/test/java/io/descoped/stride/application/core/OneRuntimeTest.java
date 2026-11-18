package io.descoped.stride.application.core;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.spi.ApplicationInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * One runtime creates the application. A class path that carries two is refused, naming both, instead of using
 * whichever happens to come first.
 */
class OneRuntimeTest {

    /** A second runtime, as another jar on the class path would bring. */
    public static class OtherRuntime implements ApplicationInitializer {
        @Override
        public StrideApplication initialize(ApplicationConfiguration configuration, Components components) {
            throw new AssertionError("must not be used");
        }
    }

    @Test
    void twoRuntimesOnTheClassPathAreRefusedNamingBoth(@TempDir Path jar) throws Exception {
        Path services = Files.createDirectories(jar.resolve("META-INF/services"));
        Files.writeString(services.resolve(ApplicationInitializer.class.getName()), OtherRuntime.class.getName());
        ApplicationConfiguration configuration = ApplicationConfiguration.builder().testDefaults().build();

        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try (URLClassLoader withSecondRuntime = new URLClassLoader(new URL[]{jar.toUri().toURL()}, original)) {
            thread.setContextClassLoader(withSecondRuntime);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> StrideApplication.create(configuration));

            assertEquals("More than one Stride runtime on the class path, and only one can create the application: "
                    + OtherRuntime.class.getName() + ", " + StrideApplicationInitializer.class.getName(),
                    failure.getMessage());
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
