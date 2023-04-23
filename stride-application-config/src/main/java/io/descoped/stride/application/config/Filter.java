package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.internal.FilterImpl;
import jakarta.servlet.DispatcherType;

import java.util.EnumSet;

public interface Filter {

    static Builder builder(String name) {
        return new FilterImpl.FilterBuilder(name);
    }

    String name();

    ObjectNode json();

    boolean isEnabled();

    String className();

    <R extends jakarta.servlet.Filter> Class<R> clazz();

    String pathSpec();

    EnumSet<DispatcherType> dispatches();

    ServletContextBinding context();

    interface Builder {

        String name();

        Builder enabled(boolean enabled);

        Builder className(String filterClassName);

        Builder clazz(Class<? extends jakarta.servlet.Filter> filterClass);

        Builder pathSpec(String pathSpec);

        Builder dispatches(EnumSet<DispatcherType> dispatches);

        Builder context(ServletContextValidation.Builder contextBuilder);

        Filter build();
    }
}
