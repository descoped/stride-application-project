package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.internal.FilterImpl;
import jakarta.servlet.DispatcherType;

import java.util.EnumSet;

public interface Filter {

    static Builder builder(String name) {
        return new FilterImpl.FilterBuilder(name);
    }

    /** Where a filter without {@code config.order} runs: after Stride's own filters. */
    int DEFAULT_ORDER = 1000;

    /** The path of a filter without {@code config.pathSpec}: every request. */
    String DEFAULT_PATH_SPEC = "/*";

    String name();

    ObjectNode json();

    boolean isEnabled();

    String className();

    <R extends jakarta.servlet.Filter> Class<R> clazz();

    String pathSpec();

    /**
     * The dispatcher types the filter applies to; {@code REQUEST} when none are configured.
     */
    EnumSet<DispatcherType> dispatches();

    /**
     * The filter's position in the chain: lower runs first, and filters with the same order run by name. This is the
     * only ordering rule. Stride's CORS filter is at 100 and authentication at 200.
     */
    int order();

    ServletContextBinding context();

    interface Builder {

        String name();

        Builder enabled(boolean enabled);

        Builder className(String filterClassName);

        Builder clazz(Class<? extends jakarta.servlet.Filter> filterClass);

        Builder pathSpec(String pathSpec);

        Builder dispatches(EnumSet<DispatcherType> dispatches);

        Builder order(int order);

        Builder context(ServletContextValidation.Builder contextBuilder);

        Filter build();
    }
}
