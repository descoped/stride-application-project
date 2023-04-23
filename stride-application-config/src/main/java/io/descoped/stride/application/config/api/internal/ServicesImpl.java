package io.descoped.stride.application.config.api.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.api.Service;
import io.descoped.stride.application.config.api.Services;
import io.descoped.stride.application.jackson.api.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ServicesImpl(ObjectNode json) implements Services {

    /**
     * Get service by name
     *
     * @param name service name
     * @return Service config
     */
    @Override
    public Optional<Service> service(String name) {
        return JsonElement.ofEphemeral(json)
                .with(name)
                .toObjectNode()
                .map(json -> new ServiceImpl(name, json));
    }

    /**
     * Get service by class
     *
     * @param className service class
     * @return Service config
     */
    @Override
    public Optional<Service> serviceByClass(String className) {
        for (Service service : iterator()) {
            if (className.equals(service.className())) {
                return Optional.of(service);
            }
        }
        return Optional.empty();
    }

    @Override
    public Iterable<Service> iterator() {
        List<Service> services = new ArrayList<>();
        Set<String> keys = ApplicationConfigurationJson.keys(json); // resolve keySet for (this) services element
        JsonElement jsonElement = JsonElement.ofStrict(json);
        for (String key : keys) {
            jsonElement.with(key)
                    .toObjectNode()
                    .map(json -> new ServiceImpl(key, json))
                    .map(services::add);
        }
        return services;
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record ServicesBuilder(ObjectNode builder) implements Services.Builder {

        public ServicesBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Services.Builder service(Service.Builder serviceBuilder) {
            Service service = serviceBuilder.build();
            builder.set(service.name(), service.json());
            return this;
        }

        @Override
        public Services build() {
            return new ServicesImpl(builder);
        }
    }
}
