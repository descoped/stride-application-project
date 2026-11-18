module stride.application.config {

    requires transitive io.descoped.dynamic.config;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;

    requires jakarta.servlet;

    requires org.slf4j;

    exports io.descoped.stride.application.jackson;
    exports io.descoped.stride.application.config;
    exports io.descoped.stride.application.config.exception;
    exports io.descoped.stride.application.config.utils;
}
