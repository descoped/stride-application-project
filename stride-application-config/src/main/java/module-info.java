module stride.application.config {

    requires property.config;
    requires property.config.json;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires jetty.servlet.api;
    requires org.slf4j;

    exports io.descoped.stride.application.api.config;
    exports io.descoped.stride.application.api.exception;
    exports io.descoped.stride.application.api.utils;
}
