package io.descoped.stride.application.server;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.Servlets;

public record Deployment(ObjectNode json) {

  public record Builder(ObjectNode builder) {

    public Builder() {
      this(JsonNodeFactory.instance.objectNode());
    }

    public Builder servlets(Servlets.Builder servletsBuilder) {
      // TODO
      return this;
    }

    public Deployment build() {
      return new Deployment(builder);
    }
  }

}
