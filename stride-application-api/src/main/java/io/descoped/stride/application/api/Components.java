package io.descoped.stride.application.api;

import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ready-made objects an application hands to Stride: the forward-passing alternative to naming a class in the
 * configuration and letting HK2 create it. The application wires its own object graph with constructors and
 * passes the results in:
 * <pre>{@code
 * var orders = new OrderRepository(dataSource);
 * var components = Components.builder()
 *         .resource(new OrderResource(orders))
 *         .filter("audit", new AuditFilter(clock))
 *         .service(OrderRepository.class, orders)   // only if something should get it injected
 *         .build();
 * StrideApplication.create(configuration, components).start();
 * }</pre>
 * Stride never creates, injects into or closes these objects; their lifecycle belongs to the application.
 * <p>
 * A filter or servlet is known by its name. Its settings are read from the configuration under
 * {@code filters.<name>} or {@code servlets.<name>} when present (enabled, path, dispatcher types, order), so
 * deployment can still tune or disable it; without an entry a filter applies to every request.
 */
public final class Components {

    private static final Components NONE = builder().build();

    /**
     * An instance offered for injection as {@code contract}, optionally by name.
     */
    public record ServiceInstance<T>(Class<T> contract, String name, T instance) {
    }

    private final List<ServiceInstance<?>> services;
    private final List<Object> resources;
    private final Map<String, Filter> filters;
    private final Map<String, Servlet> servlets;

    private Components(Builder builder) {
        this.services = List.copyOf(builder.services);
        this.resources = List.copyOf(builder.resources);
        this.filters = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(builder.filters));
        this.servlets = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(builder.servlets));
    }

    public static Components none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ServiceInstance<?>> services() {
        return services;
    }

    public List<Object> resources() {
        return resources;
    }

    public Map<String, Filter> filters() {
        return filters;
    }

    public Map<String, Servlet> servlets() {
        return servlets;
    }

    public static final class Builder {

        private final List<ServiceInstance<?>> services = new ArrayList<>();
        private final List<Object> resources = new ArrayList<>();
        private final Map<String, Filter> filters = new LinkedHashMap<>();
        private final Map<String, Servlet> servlets = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Makes an instance available for injection as {@code contract}. Needed only when a resource, filter or
         * service created by Stride should receive it; objects wired by hand do not need it.
         */
        public <T> Builder service(Class<T> contract, T instance) {
            return service(contract, null, instance);
        }

        /**
         * As {@link #service(Class, Object)}, qualified by name for {@code @Named} injection points.
         */
        public <T> Builder service(Class<T> contract, String name, T instance) {
            services.add(new ServiceInstance<>(Objects.requireNonNull(contract, "contract"), name,
                    Objects.requireNonNull(instance, "instance")));
            return this;
        }

        /**
         * Registers a JAX-RS resource, provider or feature instance.
         */
        public Builder resource(Object instance) {
            resources.add(Objects.requireNonNull(instance, "instance"));
            return this;
        }

        public Builder filter(String name, Filter filter) {
            if (filters.putIfAbsent(Objects.requireNonNull(name, "name"), Objects.requireNonNull(filter, "filter")) != null) {
                throw new IllegalArgumentException("Filter '" + name + "' is already registered");
            }
            return this;
        }

        public Builder servlet(String name, Servlet servlet) {
            if (servlets.putIfAbsent(Objects.requireNonNull(name, "name"), Objects.requireNonNull(servlet, "servlet")) != null) {
                throw new IllegalArgumentException("Servlet '" + name + "' is already registered");
            }
            return this;
        }

        public Components build() {
            return new Components(this);
        }
    }
}
