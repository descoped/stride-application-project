package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.ApplicationJson;
import io.descoped.stride.application.api.config.Resource;
import io.descoped.stride.application.api.jackson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Resources(ObjectNode json) {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get resource by name
     *
     * @param name resource name
     * @return Resource config
     */
    public Optional<Resource> resource(String name) {
        return JsonElement.ofEphemeral(json)
                .with(name)
                .optionalNode()
                .map(ObjectNode.class::cast)
                .map(json -> new ResourceImpl(name, json));
    }


    /**
     * Get resource by class
     *
     * @param className resource class
     * @return Resource config
     */
    public Optional<Resource> resourceByClass(String className) {
        for (Resource resource : iterator()) {
            if (className.equals(resource.className())) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }

    public Iterable<Resource> iterator() {
        List<Resource> resources = new ArrayList<>();
        Set<String> keys = ApplicationJson.keys(json); // resolve keySet for (this) resources element
        for (String key : keys) {
            JsonElement.ofStrict(json)
                    .with(key)
                    .optionalNode()
                    .map(ObjectNode.class::cast)
                    .map(json -> new ResourceImpl(key, json))
                    .map(resources::add);
        }
        return resources;
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Builder(ObjectNode builder) {
        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder resource(Resource.Builder resourceBuilder) {
            Resource resource = resourceBuilder.build();
            builder.set(resource.name(), resource.json());
            return this;
        }

        public Resources build() {
            return new Resources(builder);
        }
    }
}
