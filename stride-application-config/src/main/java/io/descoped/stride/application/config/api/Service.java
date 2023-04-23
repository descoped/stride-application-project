package io.descoped.stride.application.config.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.api.internal.ServiceImpl;

public interface Service {

    static Builder builder(String name) {
        return new ServiceImpl.ServiceBuilder(name);
    }

    static Builder builder(Class<?> clazz) {
        return new ServiceImpl.ServiceBuilder(clazz.getName());
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

        Builder runLevel(int runlevel);

        Builder metadata(Metadata.Builder metadataBuilder);

        Service build();
    }
}
