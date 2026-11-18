package io.descoped.stride.application.config.internal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.jackson.JsonElement;
import jakarta.servlet.DispatcherType;

import java.util.EnumSet;
import java.util.List;

public record FilterImpl(String name, ObjectNode json) implements Filter {

    private static final String ENABLED = "enabled";
    private static final String CONFIG = "config";

    @Override
    public boolean isEnabled() {
        return JsonElement.ofEphemeral(json)
                .with(ENABLED)
                .asBoolean(false);
    }

    @Override
    public String className() {
        return JsonElement.ofEphemeral(json)
                .with("config.class")
                .asString(null);
    }

    @Override
    public <R extends jakarta.servlet.Filter> Class<R> clazz() {
        return JsonElement.ofEphemeral(json)
                .with("config.class")
                .asClass();
    }

    @Override
    public String pathSpec() {
        return JsonElement.ofEphemeral(json)
                .with("config.pathSpec")
                .asString(DEFAULT_PATH_SPEC);
    }

    @Override
    public EnumSet<DispatcherType> dispatches() {
        List<DispatcherType> dispatches = JsonElement.ofStrict(json)
                .with("config.dispatches")
                .toList(node -> DispatcherType.valueOf(node.asText()));
        return dispatches.isEmpty() ? EnumSet.of(DispatcherType.REQUEST) : EnumSet.copyOf(dispatches);
    }

    @Override
    public int order() {
        JsonElement order = JsonElement.ofEphemeral(json).with("config.order");
        if (order.asString().isEmpty()) {
            return DEFAULT_ORDER; // an entry written in a file without an order
        }
        return order.asInt().orElseThrow(() -> new IllegalArgumentException(
                "filters." + name + ".config.order must be a whole number, but is '" + order.asString().get() + "'"));
    }

    // ------------------------------------------------------------------------------------------------------------

    public record FilterBuilder(String name, ObjectNode builder) implements Filter.Builder {

        // a component declared in code is enabled unless it says otherwise
        public FilterBuilder(String name) {
            this(name, JsonNodeFactory.instance.objectNode().put(ENABLED, "true"));
        }

        @Override
        public Builder enabled(boolean enabled) {
            builder.set(ENABLED, builder.textNode(Boolean.toString(enabled)));
            return this;
        }

        @Override
        public Builder className(String filterClassName) {
            JsonElement.ofDynamic(builder)
                    .with(CONFIG)
                    .object()
                    .set("class", builder.textNode(filterClassName));
            return this;
        }

        @Override
        public Builder clazz(Class<? extends jakarta.servlet.Filter> filterClass) {
            return className(filterClass.getName());
        }

        @Override
        public Builder pathSpec(String pathSpec) {
            JsonElement.ofDynamic(builder)
                    .with(CONFIG)
                    .object()
                    .set("pathSpec", builder.textNode(pathSpec));
            return this;
        }

        @Override
        public Builder order(int order) {
            JsonElement.ofDynamic(builder)
                    .with(CONFIG)
                    .object()
                    .set("order", builder.textNode(Integer.toString(order)));
            return this;
        }

        @Override
        public Builder dispatches(EnumSet<DispatcherType> dispatches) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            dispatches.forEach(e -> arrayNode.add(builder.textNode(e.name())));
            JsonElement.ofDynamic(builder)
                    .with(CONFIG)
                    .object()
                    .set("dispatches", arrayNode);
            return this;
        }

        @Override
        public Filter build() {
            return new FilterImpl(name, builder);
        }
    }
}
