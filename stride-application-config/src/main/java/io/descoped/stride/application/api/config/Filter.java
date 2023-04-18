package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.internal.FilterImpl;
import io.descoped.stride.application.api.internal.ServletContextBinding;
import io.descoped.stride.application.api.internal.ServletContextInitialization;
import jakarta.servlet.DispatcherType;

import java.util.EnumSet;

public interface Filter {
    static Builder builder(String name) {
        return new FilterImpl.FilterBuilder(name);
    }

    boolean isEnabled();

    String className();

    @SuppressWarnings("unchecked")
    <R extends jakarta.servlet.Filter> Class<R> clazz();

    String pathSpec();

    EnumSet<DispatcherType> dispatches();

    ServletContextBinding context();

    String name();

    ObjectNode json();

    interface Builder {
        Builder enabled(boolean enabled);

        Builder className(String filterClassName);

        Builder clazz(Class<? extends jakarta.servlet.Filter> filterClass);

        Builder pathSpec(String pathSpec);

        Builder dispatches(EnumSet<DispatcherType> dispatches);

        Builder context(ServletContextInitialization.Builder contextBuilder);

        Filter build();

        String name();
    }
}