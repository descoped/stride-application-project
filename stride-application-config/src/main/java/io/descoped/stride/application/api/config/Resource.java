package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.internal.ArgsImpl;
import io.descoped.stride.application.api.internal.ResourceImpl;

import java.util.List;

public interface Resource {
    static Builder builder(String name) {
        return new ResourceImpl.ResourceBuilder(name);
    }

    boolean isEnabled();

    String className();

    <R> Class<R> clazz();

    List<Arg> args();

    String name();

    ObjectNode json();

    interface Builder {
        Builder enabled(boolean enabled);

        Builder className(String resourceClassName);

        <R> Builder clazz(Class<R> resourceClass);

        Builder args(ArgsImpl.ArgsBuilder argsBuilder);

        Resource build();

        String name();

        ObjectNode builder();
    }
}
