package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.internal.ArgImpl;

public interface Arg {
    static Builder builder() {
        return new ArgImpl.ArgBuilder();
    }

    ObjectNode json();

    Class<?> clazz();

    String named();

    interface Builder {
        ObjectNode builder();

        Builder arg(Class<?> clazz, String named);

        Arg build();

    }
}
