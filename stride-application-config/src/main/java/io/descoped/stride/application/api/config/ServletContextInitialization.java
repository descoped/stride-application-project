package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.internal.ServletContextInitializationImpl;
import io.descoped.stride.application.api.internal.ServletContextValidationImpl;

import java.util.List;

public interface ServletContextInitialization {
    static Builder builder() {
        return new ServletContextInitializationImpl.ServletContextInitializationBuilder();
    }

    List<Class<?>> initializers();

    ServletContextValidationImpl validation();

    ObjectNode json();

    interface Builder {
        <R> Builder initializer(Class<R> initializerClass);

        Builder validate(ServletContextValidation.Builder servletContextValidationBuilder);

        ServletContextInitialization build();
    }
}
