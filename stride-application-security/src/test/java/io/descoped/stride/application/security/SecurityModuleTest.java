package io.descoped.stride.application.security;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module as an application meets it: on the class path, switched on by configuration, inside a started Stride
 * application.
 */
class SecurityModuleTest {

    @Path("/whoami")
    public static class WhoAmIResource {
        @GET
        public String whoAmI(@Context SecurityContext security) {
            return security.getUserPrincipal() == null ? "anonymous" : security.getUserPrincipal().getName();
        }
    }

    /** Counts the requests that get past authentication. */
    static class Counter extends HttpFilter {
        final AtomicInteger requests = new AtomicInteger();

        @Override
        protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            requests.incrementAndGet();
            chain.doFilter(request, response);
        }
    }

    private static ApplicationConfiguration.Builder configuration() {
        return ApplicationConfiguration.builder()
                .testDefaults()
                .resources(Resources.builder().resource(Resource.builder("whoami").clazz(WhoAmIResource.class)));
    }

    @Test
    void moduleDeclaresItsKeysAndIsOffByDefault() {
        ApplicationConfiguration configuration = configuration().build();

        assertFalse(SecurityConfiguration.from(configuration).enabled());
        Filter filter = configuration.filters().filter("security").orElseThrow();
        assertFalse(filter.isEnabled());
        assertEquals(BearerAuthenticationFilter.class.getName(), filter.className());
        assertEquals(200, filter.order());

        try (TestApplication application = new TestApplication.Builder().configuration(configuration()).build()) {
            application.start();
            assertEquals("anonymous", TestClient.create(application).get("/whoami").expect200Ok().body());
        }
    }

    @Test
    void oneSwitchEnablesTheFilterAndItCanComeFromASystemProperty() {
        System.setProperty("security.enabled", "true");
        try {
            ApplicationConfiguration configuration = configuration().build();

            assertTrue(configuration.filters().filter("security").orElseThrow().isEnabled());
        } finally {
            System.clearProperty("security.enabled");
        }
    }

    @Test
    void enabledWithoutAnIssuerStopsTheApplicationBeforeItListens() {
        ApplicationConfiguration.Builder configuration = configuration().overrideProperty("security.enabled", "true");

        try (TestApplication application = new TestApplication.Builder().configuration(configuration).build()) {
            assertThrows(RuntimeException.class, application::start);
            assertEquals(-1, application.getLocalPort());
        }
    }

    @Test
    void authenticatesBetweenCorsAndApplicationFilters() throws Exception {
        Counter counter = new Counter();
        // The application builds the verifier itself, here from a local key, and hands the filter in by name.
        Components components = Components.builder()
                .filter("security", new BearerAuthenticationFilter(JwtTokenVerifierTest.settings(), JwtTokenVerifierTest.verifier()))
                .filter("counter", counter)
                .build();
        ApplicationConfiguration.Builder configuration = configuration()
                .overrideProperty("security.enabled", "true")
                .overrideProperty("filters.cors.config.origin", "https://app.example.test");

        try (TestApplication application = new TestApplication.Builder()
                .configuration(configuration).components(components).build()) {
            application.start();
            TestClient client = TestClient.create(application);

            var missing = client.get("/whoami").response();
            assertEquals(401, missing.statusCode());
            assertEquals("Bearer", missing.headers().firstValue("WWW-Authenticate").orElseThrow());
            assertEquals(0, counter.requests.get(), "a rejected request does not reach application filters");

            var rejectedWithOrigin = client.get("/whoami", "Origin", "https://app.example.test").response();
            assertEquals(401, rejectedWithOrigin.statusCode());
            assertEquals("https://app.example.test",
                    rejectedWithOrigin.headers().firstValue("Access-Control-Allow-Origin").orElseThrow(),
                    "CORS runs before authentication, so a browser can read the rejection");

            var preflight = client.options("/whoami", "Origin", "https://app.example.test",
                    "Access-Control-Request-Method", "GET", "Access-Control-Request-Headers", "Authorization").response();
            assertEquals(204, preflight.statusCode(), "a preflight carries no credentials and is answered by CORS");

            String token = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().build());
            assertEquals("alice", client.get("/whoami", "Authorization", "Bearer " + token).expect200Ok().body());
            assertEquals(1, counter.requests.get());
        }
    }

    // An application that wires the filter itself must never start without authentication because a switch it
    // did not know about is off.
    @Test
    void aHandedInFilterAuthenticatesWithoutTheSwitch() throws Exception {
        Components components = Components.builder()
                .filter("security", new BearerAuthenticationFilter(JwtTokenVerifierTest.settings(), JwtTokenVerifierTest.verifier()))
                .build();

        try (TestApplication application = new TestApplication.Builder()
                .configuration(configuration()).components(components).build()) {
            application.start();
            TestClient client = TestClient.create(application);

            assertEquals(401, client.get("/whoami").response().statusCode());
            String token = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().build());
            assertEquals("alice", client.get("/whoami", "Authorization", "Bearer " + token).expect200Ok().body());
        }
    }

    @Test
    void moduleDefaultsAreFoundThroughAClassLoaderGivenToTheBuilder() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .classLoader(SecurityModuleTest.class.getClassLoader())
                .build();

        assertEquals(30, SecurityConfiguration.from(configuration).clockSkewSeconds());
    }
}
