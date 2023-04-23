module stride.application.config {

    requires property.config;
    requires property.config.json;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.dataformat.yaml;

    requires jetty.servlet.api;

    requires org.slf4j;

    exports io.descoped.stride.application.config.api;
    exports io.descoped.stride.application.jackson.api;
    exports io.descoped.stride.application.exception.api;
    exports io.descoped.stride.application.utils.api;
}
