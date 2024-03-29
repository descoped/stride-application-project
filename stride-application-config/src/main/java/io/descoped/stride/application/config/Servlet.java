package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.internal.ServletImpl;

public interface Servlet {

    static Builder builder(String name) {
        return new ServletImpl.ServletBuilder(name);
    }

    String name();

    boolean isEnabled();

    String className();

    <R extends jakarta.servlet.Servlet> Class<R> clazz();

    String pathSpec();

    ServletContextBinding binding();

    ServletContextValidation validation();

    ObjectNode json();

    interface Builder {

        String name();

        Builder enabled(boolean enabled);

        Builder className(String servletClassName);

        Builder clazz(Class<? extends jakarta.servlet.Servlet> servletClass);

        Builder pathSpec(String pathSpec);

        Builder validate(ServletContextValidation.Builder servletContextValidationBuilder);

        Builder binding(ServletContextBinding.Builder ServletContextBindingBuilder);

        Servlet build();
    }
}
