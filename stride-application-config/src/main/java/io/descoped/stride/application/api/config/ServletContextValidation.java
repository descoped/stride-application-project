package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.internal.ServletContextValidationImpl;

import java.util.Set;

public interface ServletContextValidation {

    static Builder builder() {
        return new ServletContextValidationImpl.ServletContextValidationBuilder();
    }

    ObjectNode json();

    Set<NamedService> serviceLocatorNamedServices();

    Set<String> servletContextAttributes();

    record NamedService(Class<?> type, String named) {
    }

    interface Builder {
        
        Builder requireNamedService(Class<?> type, String named);

        Builder requireAttribute(String name);

        Builder requireAttributes(Set<String> nameSet);

        ServletContextValidation build();
    }
}
