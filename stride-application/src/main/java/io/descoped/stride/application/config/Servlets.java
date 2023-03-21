package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Servlet;

public record Servlets(ObjectNode json) {

  public record Builder(ObjectNode builder) implements JsonElement {

    public Builder() {
      this(JsonNodeFactory.instance.objectNode());
    }

    public Builder servlet(Class<? extends Servlet> servletClass, String path) {
      // TODO
      return this;
    }

    @Override
    public JsonNode json() {
      return builder;
    }

    @Override
    public JsonElementStrategy strategy() {
      return JsonElementStrategy.FAIL_FAST;
    }

    public Servlets build() {
      return new Servlets(builder);
    }
  }

}
