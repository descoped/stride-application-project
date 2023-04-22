package io.descoped.stride.application.api.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.internal.FiltersImpl;

import java.util.Optional;

public interface Filters {

    static Filters.Builder builder() {
        return new FiltersImpl.FiltersBuilder();
    }

    ObjectNode json();

    Optional<Filter> filter(String name);

    Optional<Filter> filterByClass(String className);

    Iterable<Filter> iterator();

    interface Builder {

        Builder filter(Filter.Builder filterBuilder);

        Filters build();
    }
}
