/**
 * Optional: serves the application's OpenAPI document at {@code /openapi.json} and {@code /openapi.yaml}.
 */
module stride.application.openapi {

    requires stride.application.api;
    requires stride.application.config;

    requires jakarta.inject;
    requires jakarta.servlet;
    requires jakarta.ws.rs;

    requires io.swagger.v3.core;
    requires io.swagger.v3.jaxrs2;
    requires io.swagger.v3.oas.annotations;
    requires io.swagger.v3.oas.integration;
    requires io.swagger.v3.oas.models;

    exports io.descoped.stride.application.openapi;
    // Jersey creates the resource and Swagger the filter, both by reflection
    opens io.descoped.stride.application.openapi;
}
