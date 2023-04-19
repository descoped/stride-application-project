package io.descoped.stride.application.api.config;

import io.descoped.stride.application.api.config.internal.FilterImpl;
import io.descoped.stride.application.api.config.internal.FiltersImpl;

public class Builders {

    public static Filters.Builder filtersBuilder() {
        return new FiltersImpl.FiltersBuilder();
    }

    public static Filter.Builder filterBuilder(String name) {
        return new FilterImpl.FilterBuilder(name);
    }
}
