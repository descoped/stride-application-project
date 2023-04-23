package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.internal.ServicesImpl;

import java.util.Optional;

public interface Services {

    static Builder builder() {
        return new ServicesImpl.ServicesBuilder();
    }

    ObjectNode json();

    Optional<Service> service(String name);

    Optional<Service> serviceByClass(String className);

    Iterable<Service> iterator();

    interface Builder {

        Builder service(Service.Builder serviceBuilder);

        Services build();
    }
}
