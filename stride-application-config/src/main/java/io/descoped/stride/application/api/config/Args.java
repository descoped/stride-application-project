package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.descoped.stride.application.api.internal.ArgsImpl;

import java.util.List;

public interface Args {
    static Builder builder() {
        return new ArgsImpl.ArgsBuilder();
    }

    List<Arg> args();

    ArrayNode json();

    interface Builder {
        Builder arg(Class<?> clazz, String named);

        Args build();
    }

}
