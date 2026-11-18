package io.descoped.stride.application.config.internal;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.stride.application.config.Servlet;
import io.descoped.stride.application.config.ServletContextBinding;
import io.descoped.stride.application.config.ServletContextValidation;
import io.descoped.stride.application.jackson.JsonElement;

public record ServletImpl(String name, ObjectNode json) implements Servlet {

    private static final String ENABLED = "enabled";
    private static final String CONFIG = "config";

    @Override
    public boolean isEnabled() {
        return JsonElement.ofEphemeral(json)
                .with(ENABLED)
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

        // a component declared in code is enabled unless it says otherwise
        public ServletBuilder(String name) {
            this(name, JsonNodeFactory.instance.objectNode().put(ENABLED, "true"));
        }

        @Override
        public Servlet.Builder enabled(boolean enabled) {
            builder.set(ENABLED, builder.textNode(Boolean.toString(enabled)));
            return this;
        }

        @Override
        public Servlet.Builder className(String servletClassName) {
            JsonElement.ofDynamic(builder)
                    .with(CONFIG)
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
                    .with(CONFIG)
                    .object()
                    .set("pathSpec", builder.textNode(pathSpec));
            return this;
        }

        @Override
        public Servlet.Builder validate(ServletContextValidation.Builder servletContextValidationBuilder) {
            JsonElement.ofDynamic(builder)
                    .with(CONFIG)
                    .object()
                    .set("validation", servletContextValidationBuilder.build().json());
            return this;
        }

        @Override
        public Servlet.Builder binding(ServletContextBinding.Builder servletContextBindingBuilder) {
            JsonElement.ofDynamic(builder)
                    .with(CONFIG)
                    .object()
                    .set("binding", servletContextBindingBuilder.build().json());
            return this;
        }

        @Override
        public Servlet build() {
            return new ServletImpl(name, builder);
        }
    }
}
