package io.descoped.stride.application.config.api.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.api.Metadata;
import io.descoped.stride.application.jackson.api.JsonElement;

public record MetadataImpl(ObjectNode json) implements Metadata {

    @Override
    public String value(String name) {
        return JsonElement.ofStrict(json).asString(name, null);
    }

    // ------------------------------------------------------------------------------------------------------------

    public record MetadataBuilder(ObjectNode builder) implements Metadata.Builder {

        public MetadataBuilder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Metadata.Builder property(String name, String value) {
            builder.set(name, builder.textNode(value));
            return this;
        }

        @Override
        public Metadata build() {
            return new MetadataImpl(builder);
        }
    }
}
