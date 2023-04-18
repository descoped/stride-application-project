package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.internal.ServletsImpl;

import java.util.Optional;

public interface Servlets {
    static Builder builder() {
        return new ServletsImpl.ServletsBuilder();
    }

    Optional<Servlet> servlet(String name);

    Optional<Servlet> servletByClass(String className);

    Iterable<Servlet> iterator();

    ObjectNode json();

    interface Builder {
        Builder servlet(Servlet.Builder servletBuilder);

        Servlets build();
    }
}
