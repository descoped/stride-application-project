package io.descoped.stride.application.config.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.Arg;
import io.descoped.stride.application.config.Args;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public record ArgsImpl(ArrayNode json) implements Args {

    @Override
    public List<Arg> args() {
        List<Arg> argList = new ArrayList<>();
        for (Iterator<JsonNode> it = json.elements(); it.hasNext(); ) {
            ObjectNode child = (ObjectNode) it.next();
            argList.add(new ArgImpl(child));
        }
        return argList;
    }

    public record ArgsBuilder(ArrayNode builder) implements Args.Builder {

        public ArgsBuilder() {
            this(JsonNodeFactory.instance.arrayNode());
        }

        @Override
        public ArgsBuilder arg(Class<?> clazz, String named) {
            Arg arg = Arg.builder().arg(clazz, named).build();
            builder.add(arg.json());
            return this;
        }

        @Override
        public ArgsImpl build() {
            return new ArgsImpl(builder);
        }
    }
}
