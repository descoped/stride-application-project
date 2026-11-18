package io.descoped.stride.application.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAuth 2.0 Protected Resource Metadata (RFC 9728): where the document is, what it holds and how a challenge
 * points to it. The requests go to a started application whose resource identifier has a path.
 */
class ProtectedResourceMetadataTest {

    static final String RESOURCE = "https://api.example.test/orders";
    static final String METADATA_PATH = "/.well-known/oauth-protected-resource/orders";
    static final String METADATA_URL = "https://api.example.test" + METADATA_PATH;

    @Path("/")
    public static class OrdersResource {
        @GET
        @Path("orders")
        public String orders() {
            return "orders";
        }
    }

    static SecurityConfiguration settings(Set<String> requiredScopes, String resource) {
        return new SecurityConfiguration(true, JwtTokenVerifierTest.ISSUER, Set.of("stride-api"), Set.of("RS256"),
                "at+jwt", Set.of(), requiredScopes, "scope", "roles", "stride", 0, resource);
    }

    static TestApplication application;
    static TestClient client;

    @BeforeAll
    static void start() {
        SecurityConfiguration settings = settings(Set.of("write", "read"), RESOURCE);
        Components components = Components.builder()
                .filter("security", new BearerAuthenticationFilter(settings, JwtTokenVerifierTest.verifier(settings)))
                .build();
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .resources(Resources.builder().resource(Resource.builder("orders").clazz(OrdersResource.class)));
        application = new TestApplication.Builder().configuration(configuration).components(components).build();
        application.start();
        client = TestClient.create(application);
    }

    @AfterAll
    static void stop() {
        application.close();
    }

    private static JsonNode json(byte[] document) throws Exception {
        return new ObjectMapper().readTree(document);
    }

    @Test
    void section3_1TheWellKnownStringIsInsertedBetweenHostAndPath() {
        assertEquals("https://resource.example.com/.well-known/oauth-protected-resource",
                ProtectedResourceMetadata.location(URI.create("https://resource.example.com")).toString());
        assertEquals("https://resource.example.com/.well-known/oauth-protected-resource/resource1",
                ProtectedResourceMetadata.location(URI.create("https://resource.example.com/resource1")).toString());
        assertEquals("https://resource.example.com/.well-known/oauth-protected-resource",
                ProtectedResourceMetadata.location(URI.create("https://resource.example.com/")).toString(),
                "a slash that ends the identifier after the host is removed");
        assertEquals("https://resource.example.com:8443/.well-known/oauth-protected-resource/a/b?tenant=1",
                ProtectedResourceMetadata.location(URI.create("https://resource.example.com:8443/a/b?tenant=1")).toString(),
                "the port stays with the host and the query follows the path");
    }

    @Test
    void section3_2TheMetadataIsServedWithoutATokenAs200Json() throws Exception {
        var response = client.get(METADATA_PATH).response();

        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
        assertFalse(response.headers().firstValue("WWW-Authenticate").isPresent());

        JsonNode metadata = json(response.body().getBytes(StandardCharsets.UTF_8));
        // section 3.3: a client uses the document only when "resource" is identical to the identifier it started from
        assertEquals(RESOURCE, metadata.path("resource").asText());
        assertEquals("[\"" + JwtTokenVerifierTest.ISSUER + "\"]", metadata.path("authorization_servers").toString());
        assertEquals("[\"read\",\"write\"]", metadata.path("scopes_supported").toString());
        assertEquals("[\"header\"]", metadata.path("bearer_methods_supported").toString());
        assertEquals(4, metadata.size(), "nothing else is published: " + metadata);
    }

    @Test
    void section3_2AParameterWithoutAValueIsLeftOut() throws Exception {
        JsonNode metadata = json(ProtectedResourceMetadata.of(settings(Set.of(), RESOURCE)).document());

        assertFalse(metadata.has("scopes_supported"), metadata.toString());
        assertEquals(RESOURCE, metadata.path("resource").asText());
    }

    @Test
    void section3_1TheMetadataIsQueriedWithGet() {
        HttpResponse<String> head = client.head(METADATA_PATH).response();
        assertEquals(200, head.statusCode());
        assertEquals("application/json", head.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("", head.body());

        HttpResponse<String> post = client.send("POST", METADATA_PATH, HttpRequest.BodyPublishers.ofString("{}"),
                HttpResponse.BodyHandlers.ofString()).response();
        assertEquals(405, post.statusCode());
        assertEquals("GET, HEAD", post.headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void section3_1OnlyTheLocationOfTheConfiguredResourceIsOpen() {
        for (String path : new String[]{"/.well-known/oauth-protected-resource", METADATA_PATH + "/child",
                METADATA_PATH + "?tenant=1", "/.well-known/oauth-protected-resource/other"}) {
            assertEquals(401, client.get(path).response().statusCode(), path);
        }
    }

    @Test
    void section5_1EveryChallengeNamesTheMetadata() {
        var missing = client.get("/orders").response();
        assertEquals(401, missing.statusCode());
        assertEquals("Bearer realm=\"stride\", resource_metadata=\"" + METADATA_URL + "\"",
                missing.headers().firstValue("WWW-Authenticate").orElseThrow());

        var invalid = client.get("/orders", "Authorization", "Bearer not-a-jwt").response();
        assertEquals(401, invalid.statusCode());
        assertEquals("Bearer realm=\"stride\", error=\"invalid_token\", error_description=\"The access token is invalid\", "
                + "resource_metadata=\"" + METADATA_URL + "\"", invalid.headers().firstValue("WWW-Authenticate").orElseThrow());
    }

    @Test
    void section5_1AMissingScopeNamesTheScopesAndTheMetadata() throws Exception {
        String readOnly = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().claim("scope", "read").build());

        var response = client.get("/orders", "Authorization", "Bearer " + readOnly).response();

        assertEquals(403, response.statusCode());
        assertEquals("Bearer realm=\"stride\", error=\"insufficient_scope\", "
                + "error_description=\"The access token lacks a required scope\", scope=\"read write\", "
                + "resource_metadata=\"" + METADATA_URL + "\"", response.headers().firstValue("WWW-Authenticate").orElseThrow());
    }

    @Test
    void aValidTokenStillReachesTheResource() throws Exception {
        String token = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().claim("scope", "read write").build());

        assertEquals("orders", client.get("/orders", "Authorization", "Bearer " + token).expect200Ok().body());
    }

    @Test
    void section1_2AResourceIdentifierIsAnHttpsUrlWithoutAFragment() {
        assertNull(ProtectedResourceMetadata.of(settings(Set.of(), "")), "no identifier, no metadata");
        assertEquals("https://api.example.test/orders?tenant=1",
                settings(Set.of(), "https://api.example.test/orders?tenant=1").resource(), "a query is allowed");

        for (String resource : new String[]{"http://api.example.test/orders", "https://api.example.test/orders#part",
                "/orders", "https://user@api.example.test/orders", "https://api.example.test/or ders"}) {
            var failure = assertThrows(IllegalArgumentException.class, () -> settings(Set.of(), resource), resource);
            assertTrue(failure.getMessage().startsWith("security.resource "), failure.getMessage());
        }
    }
}
