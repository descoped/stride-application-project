package io.descoped.stride.application.api.config.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.api.config.Servlet;
import io.descoped.stride.application.api.config.ServletContextBinding;
import io.descoped.stride.application.api.config.ServletContextValidation;
import io.descoped.stride.application.api.jackson.JsonElement;

public record ServletImpl(String name, ObjectNode json) implements Servlet {

    @Override
    public boolean isEnabled() {
        return JsonElement.ofEphemeral(json)
                .with("enabled")
                .asBoolean(false);
    }

    @Override
    public String className() {
        return JsonElement.ofEphemeral(json)
                .with("config.class")
                .asString(null);
    }

    @Override
    public <R extends jakarta.servlet.Servlet> Class<R> clazz() {
        return JsonElement.ofEphemeral(json)
                .with("config.class")
                .asClass();
    }

    @Override
    public String pathSpec() {
        return JsonElement.ofEphemeral(json)
                .with("config.pathSpec")
                .asString(null);
    }

    @Override
    public ServletContextBinding binding() {
        return JsonElement.ofEphemeral(json)
                .with("config.binding")
                .toObjectNode()
                .map(ServletContextBindingImpl::new)
                .orElse(null);
    }

    @Override
    public ServletContextValidationImpl validation() {
        return JsonElement.ofEphemeral(json)
                .with("config.validation")
                .toObjectNode()
                .map(ServletContextValidationImpl::new)
                .orElse(null);
    }

    // ------------------------------------------------------------------------------------------------------------

    public record ServletBuilder(String name, ObjectNode builder) implements Servlet.Builder {

        public ServletBuilder(String name) {
            this(name, JsonNodeFactory.instance.objectNode());
        }

        @Override
        public Servlet.Builder enabled(boolean enabled) {
            builder.set("enabled", builder.textNode(Boolean.toString(enabled)));
            return this;
        }

        @Override
        public Servlet.Builder className(String servletClassName) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("class", builder.textNode(servletClassName));
            return this;
        }

        @Override
        public Servlet.Builder clazz(Class<? extends jakarta.servlet.Servlet> servletClass) {
            return className(servletClass.getName());
        }

        @Override
        public Servlet.Builder pathSpec(String pathSpec) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("pathSpec", builder.textNode(pathSpec));
            return this;
        }

        @Override
        public Servlet.Builder validate(ServletContextValidation.Builder servletContextValidationBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("validation", servletContextValidationBuilder.build().json());
            return this;
        }

        @Override
        public Servlet.Builder binding(ServletContextBinding.Builder ServletContextBindingBuilder) {
            JsonElement.ofDynamic(builder)
                    .with("config")
                    .object()
                    .set("binding", ServletContextBindingBuilder.build().json());
            return this;
        }

        @Override
        public Servlet build() {
            return new ServletImpl(name, builder);
        }
    }
}
