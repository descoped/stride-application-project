/**
 * Optional: JWT bearer authentication for a resource server. Add the module and set {@code security.enabled=true},
 * {@code security.issuer} and {@code security.audiences}.
 */
module stride.application.security {

    requires transitive stride.application.config;
    requires transitive jakarta.servlet;

    requires jakarta.inject;
    requires com.fasterxml.jackson.databind;
    requires com.nimbusds.jose.jwt;

    exports io.descoped.stride.application.security;
    // Stride creates the filter by reflection
    opens io.descoped.stride.application.security;
}
