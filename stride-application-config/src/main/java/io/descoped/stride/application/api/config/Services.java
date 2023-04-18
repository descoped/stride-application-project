package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.internal.ServicesImpl;

import java.util.Optional;

public interface Services {
    static Builder builder() {
        return new ServicesImpl.ServicesBuilder();
    }

    Optional<Service> service(String name);

    Optional<Service> serviceByClass(String className);

    Iterable<Service> iterator();

    ObjectNode json();

    interface Builder {
        Builder service(Service.Builder serviceBuilder);

        Services build();
    }
}
