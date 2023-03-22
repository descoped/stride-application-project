package io.descoped.stride.application.config;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

public record Services(ArrayNode json) {

  static Builder builder() {
    return new Builder();
  }

  static Metadata.Builder metadataBuilder() {
    return new Metadata.Builder();
  }

  public JsonElement serviceName(String serviceName) {
    Objects.requireNonNull(serviceName);
    for (JsonNode itemNode : json) {
      JsonElement itemElement = JsonElement.ofEphemeral(itemNode);
      if (serviceName.equals(itemElement.asString("serviceName", null))) {
        return itemElement;
      }
    }
    return JsonElement.ofEphemeral(null);
  }

  public JsonElement serviceClass(String serviceClass) {
    Objects.requireNonNull(serviceClass);
    for (JsonNode itemNode : json) {
      JsonElement itemElement = JsonElement.ofEphemeral(itemNode);
      if (serviceClass.equals(itemElement.asString("serviceClass", null))) {
        return itemElement;
      }
    }
    return JsonElement.ofEphemeral(null);
  }

  public record Builder(ArrayNode builder) {

    public Builder() {
      this(JsonNodeFactory.instance.arrayNode());
    }

    public Builder service(String name, Class<?> serviceClass) {
      ObjectNode serviceNode = JsonNodeFactory.instance.objectNode();
      ofNullable(name).map(builder::textNode).map(node -> serviceNode.set("serviceName", node));
      serviceNode.set("serviceClass", builder.textNode(serviceClass.getName()));
      builder.add(serviceNode);
      return this;
    }

    public Builder service(String name, Class<?> serviceClass, Metadata.Builder metadataBuilder) {
      ObjectNode serviceNode = JsonNodeFactory.instance.objectNode();
      ofNullable(name).map(builder::textNode).map(node -> serviceNode.set("serviceName", node));
      serviceNode.set("serviceClass", builder.textNode(serviceClass.getName()));
      serviceNode.set("metadata", metadataBuilder.build().json);
      builder.add(serviceNode);
      return this;
    }

    public Services build() {
      return new Services(builder);
    }
  }

  public record Metadata(ObjectNode json) {

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
