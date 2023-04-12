package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.exception.ExceptionFunction;
import io.descoped.stride.application.jackson.JsonElement;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public record ServletContext(ObjectNode json) {

    public static Builder builder() {
        return new Builder();
    }

    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }

    public String serviceRef(String name) {
        return ofNullable(json)
                .map(node -> node.get(name))
                .map(JsonNode::asText)
                .orElse(null);
    }

    public List<Class<ServletContextInitializer>> initializers() {
        return JsonElement.ofStrict(json)
                .with("initializers")
                .toList(JsonNode::asText)
                .stream()
                .map(ExceptionFunction.call(() -> classname -> (Class<ServletContextInitializer>) Class.forName(classname)))
                .collect(Collectors.toList());
    }

    public record Builder(ObjectNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder bind(String name, String serviceRef) {
            builder.set(name, builder.textNode(serviceRef));
            return this;
        }

        public Builder bind(String name, Class<?> serviceRef) {
            return bind(name, serviceRef.getName());
        }

        public <R extends ServletContextInitializer> Builder initializer(Class<R> initializerClass) {
            ArrayNode initializerClassArrayNode;
            if (builder.has("initializers")) {
                initializerClassArrayNode = (ArrayNode) builder.get("initializers");
            } else {
                initializerClassArrayNode = JsonNodeFactory.instance.arrayNode();
                builder.set("initializers", initializerClassArrayNode);
            }
            initializerClassArrayNode.add(builder.textNode(initializerClass.getName()));
            return this;
        }

        public ServletContext build() {
            return new ServletContext(builder);
        }
    }
}
