package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.jackson.JsonElement;

public record Metadata(ObjectNode json) {

    public static Builder builder() {
        return new Builder();
    }

    public String value(String name) {
        return JsonElement.ofStrict(json).asString(name, null);
    }

    // ------------------------------------------------------------------------------------------------------------

    public record Builder(ObjectNode builder) {

        public Builder() {
            this(JsonNodeFactory.instance.objectNode());
        }

        public Builder property(String name, String value) {
            builder.set(name, builder.textNode(value));
            return this;
        }

        public Metadata build() {
            return new Metadata(builder);
        }
    }
}
