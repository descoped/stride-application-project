package io.descoped.stride.application.config.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read resource: " + resourceName, e);
        }
    }

    public static class ResourceNotFoundException extends RuntimeException {

        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}