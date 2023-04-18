package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Arg;

public interface Builder {
    Builder arg(Class<?> clazz, String named);

    Arg build();

    ObjectNode builder();
}
