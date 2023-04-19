package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.internal.ServletContextValidationImpl;

import java.util.Set;

public interface ServletContextValidation {
    static Builder builder() {
        return new ServletContextValidationImpl.ServletContextValidationBuilder();
    }

    ObjectNode json();

    Set<String> names();

    interface Builder {
        Builder require(String named);

        Builder requires(Set<String> named);

        ServletContextValidation build();
    }
}
