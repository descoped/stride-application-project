package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.internal.ArgImpl;

public interface Arg {
    static Builder builder() {
        return new ArgImpl.ArgBuilder();
    }

    Class<?> clazz();

    String named();

    ObjectNode json();

    interface Builder {
        ObjectNode builder();

        Builder arg(Class<?> clazz, String named);

        Arg build();

    }
}
