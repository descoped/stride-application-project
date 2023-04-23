package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.internal.ServletsImpl;

import java.util.Optional;

public interface Servlets {

    static Builder builder() {
        return new ServletsImpl.ServletsBuilder();
    }

    ObjectNode json();

    Optional<Servlet> servlet(String name);

    Optional<Servlet> servletByClass(String className);

    Iterable<Servlet> iterator();

    interface Builder {

        Builder servlet(Servlet.Builder servletBuilder);

        Servlets build();
    }
}
