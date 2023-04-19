package io.descoped.stride.application.api.config;

import io.descoped.stride.application.api.config.internal.FilterImpl;
import io.descoped.stride.application.api.config.internal.FiltersImpl;

public class Builders {

    static Filters.Builder filters() {
        return new FiltersImpl.FiltersBuilder();
    }

    static Filter.Builder filter(String name) {
        return new FilterImpl.FilterBuilder(name);
    }
}
