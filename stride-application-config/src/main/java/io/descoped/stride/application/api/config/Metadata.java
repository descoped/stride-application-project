package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.internal.MetadataImpl;

public interface Metadata {

    static Metadata.Builder builder() {
        return new MetadataImpl.MetadataBuilder();
    }

    ObjectNode json();

    String value(String name);

    interface Builder {

        Metadata.Builder property(String name, String value);

        Metadata build();
    }
}
