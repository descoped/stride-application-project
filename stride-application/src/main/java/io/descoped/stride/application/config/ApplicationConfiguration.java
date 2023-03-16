package io.descoped.stride.application.config;

import com.fasterxml.jackson.databind.JsonNode;
import no.cantara.config.ApplicationProperties;

import static java.util.Optional.ofNullable;

public record ApplicationConfiguration(JsonNode json) {

    public ApplicationConfiguration(ApplicationProperties properties) {
        this(new ApplicationJson(properties).json());
    }

    public ApplicationConfiguration(ApplicationJson applicationJson) {
        this(applicationJson.json());
    }

    private JsonNode nonNullNode() {
        return ofNullable(json).orElseThrow(IllegalStateException::new);
    }

    // Use JsonElementStrategy.CREATE_EPHEMERAL_NODE_IF_NOT_EXIST strategy during element navigation
    private JsonElement element() {
        return JsonElement.ofOrCreate(nonNullNode());
    }

    public String toString() {
        return nonNullNode().toString();
    }

    public String toPrettyString() {
        return nonNullNode().toPrettyString();
    }

    public Server server() {
        return new Server(element().with("server"));
    }

    public Application application() {
        return new Application(element().with("application"), server());
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
