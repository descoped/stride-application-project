package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.internal.ServiceImpl;

public interface Service {

    static Builder builder(String name) {
        return new ServiceImpl.ServiceBuilder(name);
    }

    /** A service of the given class, named after it. */
    static Builder builder(Class<?> clazz) {
        // named after the class, and of that class
        return new ServiceImpl.ServiceBuilder(clazz.getName()).clazz(clazz);
    }

    String name();

    ObjectNode json();

    boolean isEnabled();

    String className();

    Class<?> clazz();

    int runLevel();

    Metadata metadata();

    interface Builder {

        String name();

        Builder enabled(boolean enabled);

        Builder className(String serviceClassName);

        Builder clazz(Class<?> serviceClass);

        /**
         * The run level the service starts at; application services use 1 to 9. A service without a run level is
         * not started with the application: it is created the first time it is asked for.
         */
        Builder runLevel(int runlevel);

        Builder metadata(Metadata.Builder metadataBuilder);

        Service build();
    }
}
