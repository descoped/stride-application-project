package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.internal.ResourcesImpl;

import java.util.Optional;

public interface Resources {

    static Builder builder() {
        return new ResourcesImpl.ResourcesBuilder();
    }

    ObjectNode json();

    Optional<Resource> resource(String name);

    Optional<Resource> resourceByClass(String className);

    Iterable<Resource> iterator();

    interface Builder {

        Builder resource(Resource.Builder resourceBuilder);

        Resources build();
    }
}
