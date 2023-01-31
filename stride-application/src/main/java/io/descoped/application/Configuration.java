package io.descoped.application;

import no.cantara.config.ApplicationProperties;

import java.util.Map;

public interface Configuration {

    ApplicationProperties properties();

    default Map<String, String> asMap() {
        return properties().map();
    }

    default String alias() {
        return properties().get("application.alias", "default");
    }

    default String version() {
        return properties().get("application.version", "0.0.1");
    }

    default String applicationUrl() {
        return properties().get("application.url", String.format("http://%s:%s%s",
                host(), port(), contextPath()));
    }

    default String contextPath() {
        return properties().get("server.context-path", "/");
    }

    default String host() {
        return properties().get("server.host", "0.0.0.0");
    }

    default int port() {
        return properties().asInt("server.port", 9090);
    }

    static Configuration create(ApplicationProperties applicationProperties) {
        return new Default(applicationProperties);
    }

    record Default(ApplicationProperties properties) implements Configuration {

    }

}
