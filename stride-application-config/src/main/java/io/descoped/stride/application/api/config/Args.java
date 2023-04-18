package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public record Args(ArrayNode json) {

    public static Builder builder() {
        return new Builder();
    }

    public List<Arg> args() {
        List<Arg> argList = new ArrayList<>();
        for (Iterator<JsonNode> it = json.elements(); it.hasNext(); ) {
            ObjectNode child = (ObjectNode) it.next();
            argList.add(new Arg(child));
        }
        return argList;
    }

    public record Builder(ArrayNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.arrayNode());
        }

        public Builder arg(Class<?> clazz, String named) {
            Arg arg = Arg.builder().arg(clazz, named).build();
            builder.add(arg.json());
            return this;
        }

        public Args build() {
            return new Args(builder);
        }
    }
}
