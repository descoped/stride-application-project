package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import no.cantara.config.ApplicationProperties;

import java.util.Objects;

import static java.util.Optional.ofNullable;

/**
 * The ApplicationConfiguration navigates and resolves values in hierachy using jackson objects. If the internal
 * json is updated, this will automatically be reflected by any lookup.
 */
public final class ApplicationConfiguration implements JsonElement {
    private final JsonNode json;
    private final JsonElementStrategy strategy;

    public ApplicationConfiguration(JsonNode json, JsonElementStrategy strategy) {
        this.json = json;
        this.strategy = strategy;
    }

    public ApplicationConfiguration(JsonNode json) {
        this(json, JsonElementStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST);
    }

    public ApplicationConfiguration(ApplicationProperties properties) {
        this(new ApplicationJson(properties).json());
    }

    public ApplicationConfiguration(ApplicationJson applicationJson) {
        this(applicationJson.json());
    }

    @Override
    public JsonNode json() {
        return json;
    }

    @Override
    public JsonElementStrategy strategy() {
        return strategy;
    }

    private JsonNode nonNullNode() {
        return ofNullable(json).orElseThrow(IllegalStateException::new);
    }

    // Using JsonElementStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST strategy during element navigation
    JsonElement element() {
        return JsonElement.ofEphemeral(nonNullNode());
    }

    public String toString() {
        return nonNullNode().toString();
    }

    public String toPrettyString() {
        return nonNullNode().toPrettyString();
    }

    public boolean isVerboseLogging() {
        return element().with("verbose.logging").asBoolean(false);
    }

    public Server server() {
        return new Server(element().with("server"));
    }

    public Application application() {
        return new Application(element().with("application"), server());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ApplicationConfiguration) obj;
        return Objects.equals(this.json, that.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(json);
    }

    public record Server(JsonElement element) {

        public String host() {
            return element.with("host").asString("localhost");
        }

        public int port() {
            return element.with("port").asInt(9090);
        }

        public String contextPath() {
            return element.with("context-path").asString("/");
        }
    }

    public record Application(JsonElement element, Server server) {
        public String alias() {
            return element.with("alias").asString("default");
        }

        public String version() {
            return element.with("version").asString("unknown");
        }

        public String url() {
            return element.with("url").asString(String.format("http://%s:%s%s",
                    server.host(), server.port(), server.contextPath()));
        }

        public Cors cors() {
            return new Cors(element.with("cors"));
        }

        public record Cors(JsonElement element) {
            public String headers() {
                return element.with("headers").asString("origin, content-type, accept, authorization");
            }
        }
    }

}
