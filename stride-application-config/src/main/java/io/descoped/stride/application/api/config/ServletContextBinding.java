package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.internal.ServletContextBindingImpl;

import java.util.Set;

public interface ServletContextBinding {
    static Builder builder() {
        return new ServletContextBindingImpl.ServletContextBindingBuilder();
    }

    Set<String> names();

    String serviceRef(String name);

    ObjectNode json();

    interface Builder {
        Builder bind(String name, String named);

        Builder bind(String name, Class<?> named);

        ServletContextBinding build();
    }
}
