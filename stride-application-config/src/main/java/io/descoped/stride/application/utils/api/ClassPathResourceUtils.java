package io.descoped.stride.application.utils.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ClassPathResourceUtils {

    private static final Logger log = LoggerFactory.getLogger(ClassPathResourceUtils.class);

    static ClassLoader tccl() {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader == null) {
            return ClassLoader.getSystemClassLoader();
        }
        return contextClassLoader;
    }

    public static String readResource(String resourceName) {
        InputStream resourceStream = tccl().getResourceAsStream(resourceName);
        if (resourceStream == null) {
            log.error("Resource NOT Found: {}", resourceName);
            throw new ResourceNotFoundException("Resource NOT Found: " + resourceName);
        }
        return new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
    }

    public static class ResourceNotFoundException extends RuntimeException {

        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}