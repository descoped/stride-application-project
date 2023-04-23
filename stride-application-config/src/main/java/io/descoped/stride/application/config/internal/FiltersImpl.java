package io.descoped.stride.application.config.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.jackson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record FiltersImpl(ObjectNode json) implements Filters {

    /**
     * Get filter by name
     *
     * @param name filter name
     * @return Filter config
     */
    @Override
    public Optional<Filter> filter(String name) {
        return JsonElement.ofEphemeral(json)
                .with(name)
                .toObjectNode()
                .map(json -> new FilterImpl(name, json));
    }

    /**
     * Lookup filter by class
     *
     * @param className filter classname
     * @return Filter config
     */
    @Override
    public Optional<Filter> filterByClass(String className) {
        for (Filter filter : iterator()) {
            if (className.equals(filter.className())) {
                return Optional.of(filter);
            }
        }
        return Optional.empty();
    }

    @Override
    public Iterable<Filter> iterator() {
        List<Filter> filters = new ArrayList<>();
        Set<String> keys = ApplicationConfigurationJson.keys(json); // resolve keySet for (this) filters element
        for (String key : keys) {
            JsonElement.ofStrict(json)
                    .with(key)
                    .toObjectNode()
                    .map(json -> new FilterImpl(key, json))
                    .map(filters::add);
        }
        return filters;
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record FiltersBuilder(ObjectNode builder) implements Filters.Builder {

        public FiltersBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Filters.Builder filter(Filter.Builder filterBuilder) {
            Filter filter = filterBuilder.build();
            builder.set(filter.name(), filter.json());
            return this;
        }

        @Override
        public Filters build() {
            return new FiltersImpl(builder);
        }
    }
}
