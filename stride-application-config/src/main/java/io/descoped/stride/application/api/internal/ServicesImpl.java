package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.ApplicationJson;
import io.descoped.stride.application.api.config.Service;
import io.descoped.stride.application.api.config.Services;
import io.descoped.stride.application.api.jackson.JsonElement;

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
                .optionalNode()
                .map(ObjectNode.class::cast)
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
        Set<String> keys = ApplicationJson.keys(json); // resolve keySet for (this) services element
        for (String key : keys) {
            JsonElement.ofStrict(json)
                    .with(key)
                    .optionalNode()
                    .map(ObjectNode.class::cast)
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
