package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.internal.ResourceImpl;

import java.util.List;

public interface Resource {

    static Builder builder(String name) {
        return new ResourceImpl.ResourceBuilder(name);
    }

    String name();

    ObjectNode json();

    boolean isEnabled();

    String className();

    <R> Class<R> clazz();

    List<Arg> args();

    interface Builder {

        String name();

        Builder enabled(boolean enabled);

        Builder className(String resourceClassName);

        <R> Builder clazz(Class<R> resourceClass);

        Builder args(Args.Builder argsBuilder);

        Resource build();
    }
}
