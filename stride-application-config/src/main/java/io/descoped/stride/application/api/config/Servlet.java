package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.internal.ServletContextBinding;
import io.descoped.stride.application.api.internal.ServletContextValidation;
import io.descoped.stride.application.api.internal.ServletImpl;

public interface Servlet {
    static Builder builder(String name) {
        return new ServletImpl.ServletBuilder(name);
    }

    boolean isEnabled();

    String className();

    <R extends jakarta.servlet.Servlet> Class<R> clazz();

    String pathSpec();

    ServletContextBinding binding();

    ServletContextValidation validation();

    String name();

    ObjectNode json();

    interface Builder {
        Builder enabled(boolean enabled);

        Builder className(String servletClassName);

        Builder clazz(Class<? extends jakarta.servlet.Servlet> servletClass);

        Builder pathSpec(String pathSpec);

        Builder validate(ServletContextValidation.Builder servletContextValidationBuilder);

        Builder binding(ServletContextBinding.Builder ServletContextBindingBuilder);

        Servlet build();

        String name();
    }
}
