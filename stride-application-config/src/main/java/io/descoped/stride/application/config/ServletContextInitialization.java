package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.internal.ServletContextInitializationImpl;

import java.util.List;

public interface ServletContextInitialization {

    static Builder builder() {
        return new ServletContextInitializationImpl.ServletContextInitializationBuilder();
    }

    ObjectNode json();

    List<Class<?>> classes();

    ServletContextValidation validation();

    interface Builder {

        <R> Builder initializerClass(Class<R> initializerClass);

        Builder validate(ServletContextValidation.Builder servletContextValidationBuilder);

        ServletContextInitialization build();
    }
}
