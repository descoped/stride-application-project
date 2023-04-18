package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.jackson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Filters(ObjectNode json) {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get filter by name
     *
     * @param name filter name
     * @return Filter config
     */
    public Optional<Filter> filter(String name) {
        return JsonElement.ofEphemeral(json)
                .with(name)
                .optionalNode()
                .map(ObjectNode.class::cast)
                .map(json -> new Filter(name, json));
    }

    /**
     * Lookup filter by class
     *
     * @param className filter classname
     * @return Filter config
     */
    public Optional<Filter> filterByClass(String className) {
        for (Filter filter : iterator()) {
            if (className.equals(filter.className())) {
                return Optional.of(filter);
            }
        }
        return Optional.empty();
    }

    public Iterable<Filter> iterator() {
        List<Filter> filters = new ArrayList<>();
        Set<String> keys = ApplicationJson.keys(json); // resolve keySet for (this) filters element
        for (String key : keys) {
            JsonElement.ofStrict(json)
                    .with(key)
                    .optionalNode()
                    .map(ObjectNode.class::cast)
                    .map(json -> new Filter(key, json))
                    .map(filters::add);
        }
        return filters;
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Builder(ObjectNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder filter(Filter.Builder filterBuilder) {
            Filter filter = filterBuilder.build();
            builder.set(filter.name(), filter.json());
            return this;
        }

        public Filters build() {
            return new Filters(builder);
        }
    }
}
