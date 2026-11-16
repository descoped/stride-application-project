package io.descoped.stride.application.config.internal;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Finds the defaults every module contributes. A module declares the keys it reads, with their defaults, in
 * {@value #RESOURCE}. Stride's own modules do, and a library or an application may do the same for its keys, which
 * makes them overridable by environment variable and system property like any other.
 * <p>
 * Several jars carry a file of this name. A build that merges jars into one (Shade, Assembly) must append these
 * files, not keep one of them: with the Maven Shade plugin, an {@code AppendingTransformer} for {@value #RESOURCE}.
 */
public final class ModuleDefaults {

    public static final String RESOURCE = "META-INF/stride/defaults.properties";

    private ModuleDefaults() {
    }

    /**
     * Returns every {@value #RESOURCE} visible to the class loader, in a stable order.
     *
     * @throws IllegalStateException if two of them declare the same key: a key has one owner, and letting the order
     *                               of the class path pick a default would be a silent, fragile choice
     */
    public static List<URL> find(ClassLoader classLoader) {
        List<URL> resources;
        try {
            resources = Collections.list(classLoader.getResources(RESOURCE));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not look up " + RESOURCE, e);
        }
        if (resources.isEmpty()) {
            throw new IllegalStateException(RESOURCE + " of stride-application-config is not on the class path");
        }
        // a class loader may list one file twice; that is one module, not two
        resources = resources.stream()
                .collect(Collectors.toMap(URL::toString, url -> url, (first, _) -> first, TreeMap::new))
                .values().stream().toList();

        Map<String, URL> owners = new HashMap<>();
        for (URL resource : resources) {
            for (String key : keys(resource)) {
                URL owner = owners.putIfAbsent(key, resource);
                if (owner != null) {
                    throw new IllegalStateException("Configuration key '" + key + "' is declared by both " + owner
                            + " and " + resource);
                }
            }
        }
        return resources;
    }

    private static Iterable<String> keys(URL resource) {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
        return properties.stringPropertyNames();
    }
}
