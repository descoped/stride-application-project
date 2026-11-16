package io.descoped.stride.application.security;

import io.descoped.stride.application.config.ApplicationConfiguration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigurationTest {
    @Test void bindsSecurityPropertiesWithoutRequiringAnEeContainer() {
        var config = ApplicationConfiguration.builder()
                .overrideProperty("security.enabled", "true")
                .overrideProperty("security.issuer", "https://issuer.example/realm")
                .overrideProperty("security.audiences", "api, another-api")
                .overrideProperty("security.public-paths", "/ping, /health")
                .overrideProperty("security.required-scopes", "read  write")
                .build();
        var security = SecurityConfiguration.from(config);
        assertTrue(security.enabled());
        assertEquals(java.util.Set.of("api", "another-api"), security.audiences());
        assertEquals(java.util.Set.of("/ping", "/health"), security.publicPaths());
        assertEquals(java.util.Set.of("read", "write"), security.requiredScopes());
        assertEquals("at+jwt", security.tokenType());
        assertEquals("scope", security.scopeClaim());
        assertEquals("roles", security.rolesClaim());
        assertEquals("default", security.realm(), "the realm is the application's alias unless set");
    }
    @Test void authenticationIsExplicitlyOptIn() {
        assertFalse(SecurityConfiguration.from(ApplicationConfiguration.builder().build()).enabled());
    }
}
