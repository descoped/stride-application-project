package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.jackson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Services(ObjectNode json) {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get service by name
     *
     * @param name service name
     * @return Service config
     */
    public Optional<Service> service(String name) {
        return JsonElement.ofEphemeral(json)
                .with(name)
                .optionalNode()
                .map(ObjectNode.class::cast)
                .map(json -> new Service(name, json));
    }

    /**
     * Get service by class
     *
     * @param className service class
     * @return Service config
     */
    public Optional<Service> serviceByClass(String className) {
        for (Service service : iterator()) {
            if (className.equals(service.className())) {
                return Optional.of(service);
            }
        }
        return Optional.empty();
    }

    public Iterable<Service> iterator() {
        List<Service> services = new ArrayList<>();
        Set<String> keys = ApplicationJson.keys(json); // resolve keySet for (this) services element
        for (String key : keys) {
            JsonElement.ofStrict(json)
                    .with(key)
                    .optionalNode()
                    .map(ObjectNode.class::cast)
                    .map(json -> new Service(key, json))
                    .map(services::add);
        }
        return services;
    }


    // ----------------------------------------------------------------------------------------------------------------

    public record Builder(ObjectNode builder) {

        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder service(Service.Builder serviceBuilder) {
            Service service = serviceBuilder.build();
            builder.set(service.name(), service.json());
            return this;
        }

        public Services build() {
            return new Services(builder);
        }
    }
}
