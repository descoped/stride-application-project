package io.descoped.stride.application.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigurationTest {
    @Test void bindsSecurityPropertiesWithoutRequiringAnEeContainer() {
        var config = ApplicationConfiguration.builder()
                .overrideProperty("security.enabled", "true")
                .overrideProperty("security.issuer", "https://issuer.example/realm")
                .overrideProperty("security.audiences", "api, another-api")
                .overrideProperty("security.public-paths", "/ping, /health")
                .overrideProperty("security.required-scopes", "read, write")
                .build();
        var security = config.security();
        assertTrue(security.enabled());
        assertEquals(java.util.Set.of("api", "another-api"), security.audiences());
        assertEquals(java.util.Set.of("/ping", "/health"), security.publicPaths());
        assertEquals(java.util.Set.of("read", "write"), security.requiredScopes());
        assertEquals("at+jwt", security.tokenType());
        assertEquals(30_000, config.server().idleTimeout());
    }
    @Test void authenticationIsExplicitlyOptIn() {
        assertFalse(ApplicationConfiguration.builder().build().security().enabled());
    }
}
