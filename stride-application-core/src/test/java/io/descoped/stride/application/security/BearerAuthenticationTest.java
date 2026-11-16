package io.descoped.stride.application.security;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.cors.ApplicationCORSServletFilter;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.servlet.ServletContainer;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class BearerAuthenticationTest {
    @Path("/") public static class IdentityResource {
        @GET @Path("identity") public String identity(@Context SecurityContext security) {
            return security.getUserPrincipal().getName();
        }
        @GET @Path("reader") @RolesAllowed("reader") public String reader() { return "reader"; }
        @GET @Path("admin") @RolesAllowed("admin") public String admin() { return "admin"; }
        @GET @Path("public") public String publicRoute() { return "public"; }
    }

    @Test void enforcesBearerAuthenticationScopesRolesAndCorsOverHttp() throws Exception {
        var server = new Server();
        var connector = new ServerConnector(server);
        connector.setHost("127.0.0.1"); connector.setPort(0); server.addConnector(connector);
        var context = new ServletContextHandler("/app");
        context.addFilter(new FilterHolder(new ApplicationCORSServletFilter(ApplicationConfiguration.builder()
                .overrideProperty("filters.cors.config.origin", "https://app.example.test").build())), "/*", EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(new FilterHolder(new BearerAuthenticationFilter(JwtTokenVerifierTest.settings(), JwtTokenVerifierTest.verifier())),
                "/*", EnumSet.allOf(DispatcherType.class));
        var config = new ResourceConfig(IdentityResource.class).register(RolesAllowedDynamicFeature.class)
                .property("jersey.config.server.wadl.disableWadl", true);
        context.addServlet(new ServletHolder(new ServletContainer(config)), "/*");
        server.setHandler(context);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            server.start();
            String base = "http://127.0.0.1:" + connector.getLocalPort() + "/app";
            java.util.function.Function<String, HttpRequest.Builder> request = path -> HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(3));
            var missing = client.send(request.apply("/identity").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, missing.statusCode());
            assertEquals("Bearer", missing.headers().firstValue("WWW-Authenticate").orElseThrow());
            var malformed = client.send(request.apply("/identity").header("Authorization", "Bearer invalid").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, malformed.statusCode());
            assertFalse(malformed.body().contains("invalid"));
            String token = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().build());
            var identity = client.send(request.apply("/identity").header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, identity.statusCode()); assertEquals("alice", identity.body());
            assertEquals(200, client.send(request.apply("/reader").header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(403, client.send(request.apply("/admin").header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            String noScope = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().claim("scope", "other").build());
            assertEquals(403, client.send(request.apply("/identity").header("Authorization", "Bearer " + noScope).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(200, client.send(request.apply("/public").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(401, client.send(request.apply("/public/child").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(401, client.send(request.apply("/identity").header("Authorization", "Bearer " + token).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            var preflight = client.send(request.apply("/identity").header("Origin", "https://app.example.test").header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "Authorization").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(204, preflight.statusCode());
            assertEquals("https://app.example.test", preflight.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            assertEquals(403, client.send(request.apply("/identity").header("Origin", "https://evil.example.test").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            var rejected = client.send(request.apply("/identity").header("Origin", "https://app.example.test").GET().build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(401, rejected.statusCode());
            assertEquals("https://app.example.test", rejected.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        } finally { server.stop(); server.destroy(); }
    }

    @Test void rejectsWildcardCredentialedCors() {
        assertThrows(IllegalArgumentException.class, () -> ApplicationCORSServletFilter.builder().origin("*").credentials("true").build());
    }
}
