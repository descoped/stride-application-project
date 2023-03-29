package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.exception.ExceptionFunction;
import io.descoped.stride.application.jackson.JsonElement;
import org.glassfish.hk2.runlevel.RunLevel;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public record Services(ArrayNode json) {

    public List<Service> list() {
        return JsonElement.of(json).toList(node -> new Service((ObjectNode) node));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Service.Builder serviceBuilder() {
        return new Service.Builder();
    }

    public static Metadata.Builder metadataBuilder() {
        return new Metadata.Builder();
    }

    private Optional<JsonNode> findNode(String withProperty, String equalTo) {
        if (equalTo == null) {
            return Optional.empty();
        }
        for (JsonNode itemNode : json) {
            JsonElement itemElement = JsonElement.ofEphemeral(itemNode);
            if (equalTo.equals(itemElement.asString(withProperty, null))) {
                return itemElement.optionalNode();
            }
        }
        return Optional.empty();
    }

    public Optional<Service> serviceName(String serviceName) {
        return findNode("serviceName", serviceName).map(ObjectNode.class::cast).map(Service::new);
    }

    public Optional<Service> serviceClass(String serviceClass) {
        return findNode("serviceClass", serviceClass).map(ObjectNode.class::cast).map(Service::new);
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Builder(ArrayNode builder) {

        public Builder() {
            this(JsonNodeFactory.instance.arrayNode());
        }

        public Builder service(Service.Builder serviceBuilder) {
            builder.add(serviceBuilder.build().json);
            return this;
        }

        public Services build() {
            return new Services(builder);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Service(ObjectNode json) {

        public boolean isEnabled() {
            return ofNullable(json)
                    .map(node -> node.get("enabled"))
                    .map(JsonNode::asText)
                    .map(Boolean::parseBoolean)
                    .map(Boolean.TRUE::equals)
                    .orElse(false);
        }

        public String name() {
            return ofNullable(json)
                    .map(node -> node.get("serviceName"))
                    .map(JsonNode::asText)
                    .orElse(null);
        }

        public String className() {
            return ofNullable(json)
                    .map(node -> node.get("serviceClass"))
                    .map(JsonNode::asText)
                    .orElse(null);
        }

        @SuppressWarnings("Convert2MethodRef")
        public Class<?> clazz() {
            return ofNullable(className())
                    .map(ExceptionFunction.call(() -> s -> Class.forName(s))) // deal with hard exception
                    .orElse(null);
        }

        public int runLevel() {
            return ofNullable(json)
                    .map(node -> node.get("runLevel"))
                    .map(JsonNode::asText)
                    .map(Integer::valueOf)
                    .orElse(RunLevel.RUNLEVEL_VAL_INITIAL);
        }

        public Metadata metadata() {
            return ofNullable(json)
                    .map(node -> node.get("metadata"))
                    .map(ObjectNode.class::cast)
                    .map(Services.Metadata::new)
                    .orElse(null);
        }

        // ------------------------------------------------------------------------------------------------------------

        public record Builder(ObjectNode builder) {
            public Builder() {
                this(JsonNodeFactory.instance.objectNode());
            }

            public Builder enabled(boolean enabled) {
                builder.set("enabled", builder.textNode(Boolean.toString(enabled)));
                return this;
            }

            public Builder name(String serviceName) {
                ofNullable(serviceName).map(builder::textNode).map(node -> builder.set("serviceName", node));
                return this;
            }

            public Builder className(String serviceClassName) {
                builder.set("serviceClass", builder.textNode(serviceClassName));
                return this;
            }

            public Builder clazz(Class<?> serviceClass) {
                return className(serviceClass.getName());
            }

            public Builder runLevel(int runlevel) {
                builder.set("runLevel", builder.textNode(String.valueOf(runlevel)));
                return this;
            }

            public Builder metadata(Metadata.Builder metadataBuilder) {
                builder.set("metadata", metadataBuilder.build().json);
                return this;
            }

            public Service build() {
                return new Service(builder);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------

    public record Metadata(ObjectNode json) {

        public String value(String name) {
            return JsonElement.of(json).asString(name, null);
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
}
