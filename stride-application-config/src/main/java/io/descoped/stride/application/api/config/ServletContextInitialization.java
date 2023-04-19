package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.internal.ServletContextInitializationImpl;

import java.util.List;

public interface ServletContextInitialization {
    static Builder builder() {
        return new ServletContextInitializationImpl.ServletContextInitializationBuilder();
    }

    ObjectNode json();

    List<Class<?>> initializers();

    ServletContextValidation validation();

    interface Builder {
        <R> Builder initializer(Class<R> initializerClass);

        Builder validate(ServletContextValidation.Builder servletContextValidationBuilder);

        ServletContextInitialization build();
    }
}
