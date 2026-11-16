package io.descoped.stride.application.config.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleDefaultsTest {

    private static URL module(Path directory, String name, String properties) throws IOException {
        Path file = directory.resolve(name).resolve(ModuleDefaults.RESOURCE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, properties);
        return directory.resolve(name).toUri().toURL();
    }

    @Test
    void findsStridesOwnDefaults() {
        List<URL> defaults = ModuleDefaults.find(ModuleDefaults.class.getClassLoader());

        assertTrue(defaults.stream().anyMatch(url -> url.toString().contains("stride-application-config")), defaults.toString());
    }

    @Test
    void findsTheDefaultsOfEveryModuleInAStableOrder(@TempDir Path directory) throws IOException {
        URL payments = module(directory, "payments", "payments.timeout=PT5S\n");
        URL audit = module(directory, "audit", "audit.enabled=true\n");

        try (URLClassLoader forwards = new URLClassLoader(new URL[]{payments, audit}, null);
             URLClassLoader backwards = new URLClassLoader(new URL[]{audit, payments}, null)) {
            List<URL> found = ModuleDefaults.find(forwards);

            assertEquals(2, found.size());
            assertEquals(found, ModuleDefaults.find(backwards), "the order does not depend on the class path");
        }
    }

    @Test
    void aKeyDeclaredByTwoModulesIsAnErrorNamingBoth(@TempDir Path directory) throws IOException {
        URL first = module(directory, "first", "shared.key=1\n");
        URL second = module(directory, "second", "other.key=x\nshared.key=2\n");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{first, second}, null)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ModuleDefaults.find(classLoader));

            assertTrue(failure.getMessage().contains("'shared.key'"), failure.getMessage());
            assertTrue(failure.getMessage().contains("/first/") && failure.getMessage().contains("/second/"), failure.getMessage());
        }
    }

    @Test
    void aClassPathWithoutAnyDefaultsIsAnError() throws IOException {
        try (URLClassLoader empty = new URLClassLoader(new URL[0], null)) {
            assertThrows(IllegalStateException.class, () -> ModuleDefaults.find(empty));
        }
    }
}
