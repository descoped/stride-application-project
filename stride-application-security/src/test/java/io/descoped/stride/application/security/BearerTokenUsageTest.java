package io.descoped.stride.application.security;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a resource server answers resource requests, per OAuth 2.1 (draft-ietf-oauth-v2-1-16) section 5 and the JWT
 * profile for access tokens (RFC 9068) section 4. Each test sends real requests to a started application.
 */
class BearerTokenUsageTest {

    @Path("/")
    public static class IdentityResource {
        @GET
        @Path("identity")
        public String identity(@Context SecurityContext security) {
            return security.getUserPrincipal().getName() + " via " + security.getAuthenticationScheme();
        }

        @GET
        @Path("reader")
        @RolesAllowed("reader")
        public String reader() {
            return "reader";
        }

        @GET
        @Path("admin")
        @RolesAllowed("admin")
        public String admin() {
            return "admin";
        }

        @GET
        @Path("public")
        public String publicRoute() {
            return "public";
        }
    }

    static TestApplication application;
    static TestClient client;

    @BeforeAll
    static void start() {
        // settings: realm "stride", required scope "read", public path "/public"
        Components components = Components.builder()
                .filter("security", new BearerAuthenticationFilter(JwtTokenVerifierTest.settings(), JwtTokenVerifierTest.verifier()))
                .build();
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .resources(Resources.builder().resource(Resource.builder("identity").clazz(IdentityResource.class)));
        application = new TestApplication.Builder().configuration(configuration).components(components).build();
        application.start();
        client = TestClient.create(application);
    }

    @AfterAll
    static void stop() {
        application.close();
    }

    private static String token() throws Exception {
        return JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().build());
    }

    private static String challenge(HttpResponse<String> response) {
        var challenges = response.headers().allValues("WWW-Authenticate");
        assertEquals(1, challenges.size(), "exactly one challenge: " + challenges);
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("", response.body(), "a rejection has no body");
        return challenges.getFirst();
    }

    @Test
    void section5_1_1AcceptsTheTokenInTheAuthorizationHeaderWithTheSchemeInAnyCase() throws Exception {
        for (String scheme : new String[]{"Bearer", "bearer", "BEARER", "bEaReR"}) {
            assertEquals("alice via Bearer", client.get("/identity", "Authorization", scheme + " " + token()).expect200Ok().body(), scheme);
        }
        assertEquals("alice via Bearer", client.get("/identity", "Authorization", "Bearer   " + token()).expect200Ok().body(),
                "credentials = \"bearer\" 1*SP token68");
    }

    @Test
    void section5_3_2ARequestWithoutCredentialsGetsAChallengeWithoutAnErrorCode() {
        var response = client.get("/identity").response();

        assertEquals(401, response.statusCode());
        // section 5.3.1: the scheme MUST be followed by one or more auth-param values
        assertEquals("Bearer realm=\"stride\"", challenge(response));
    }

    @Test
    void section5_3_2AnotherAuthenticationSchemeGetsAChallengeWithoutAnErrorCode() {
        var response = client.get("/identity", "Authorization", "Basic YWxpY2U6c2VjcmV0").response();

        assertEquals(401, response.statusCode());
        assertEquals("Bearer realm=\"stride\"", challenge(response));
    }

    @Test
    void section5_1TheTokenInAQueryParameterIsIgnored() throws Exception {
        var response = client.get("/identity?access_token=" + token()).response();

        assertEquals(401, response.statusCode());
        assertEquals("Bearer realm=\"stride\"", challenge(response));
    }

    @Test
    void section5_3_2AMalformedRequestIs400InvalidRequest() throws Exception {
        String expected = "Bearer realm=\"stride\", error=\"invalid_request\", error_description=";

        var repeated = client.get("/identity", "Authorization", "Bearer " + token(), "Authorization", "Bearer " + token()).response();
        assertEquals(400, repeated.statusCode());
        assertTrue(challenge(repeated).startsWith(expected), challenge(repeated));

        // not token68: characters outside the set, or nothing at all
        for (String credentials : new String[]{"Bearer", "Bearer abc def", "Bearer abc,def", "Bearer =abc", "Bearer a\"b"}) {
            var response = client.get("/identity", "Authorization", credentials).response();
            assertEquals(400, response.statusCode(), credentials);
            assertTrue(challenge(response).startsWith(expected), credentials);
        }
    }

    @Test
    void section5_3_2AnInvalidTokenIs401InvalidToken() throws Exception {
        String invalid = "Bearer realm=\"stride\", error=\"invalid_token\", error_description=\"The access token is invalid\"";
        String wrongAudience = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().audience("another-api").build());
        String idToken = JwtTokenVerifierTest.token(JwtTokenVerifierTest.KEY, JwtTokenVerifierTest.claims().build(), "JWT");

        for (String token : new String[]{"not-a-jwt", wrongAudience, idToken, token() + "x"}) {
            var response = client.get("/identity", "Authorization", "Bearer " + token).response();
            assertEquals(401, response.statusCode(), token);
            assertEquals(invalid, challenge(response), "the reason a token is invalid is not told");
        }
    }

    @Test
    void section5_3_2AnExpiredTokenIs401InvalidTokenAndSaysSo() throws Exception {
        String expired = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims()
                .expirationTime(Date.from(Instant.now().minusSeconds(60))).build());

        var response = client.get("/identity", "Authorization", "Bearer " + expired).response();

        assertEquals(401, response.statusCode());
        assertEquals("Bearer realm=\"stride\", error=\"invalid_token\", error_description=\"The access token expired\"",
                challenge(response));
    }

    @Test
    void section5_3_2ATokenWithoutARequiredScopeIs403InsufficientScopeNamingTheScope() throws Exception {
        String otherScope = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().claim("scope", "write").build());
        String noScope = JwtTokenVerifierTest.token(JwtTokenVerifierTest.claims().claim("scope", null).build());

        for (String token : new String[]{otherScope, noScope}) {
            var response = client.get("/identity", "Authorization", "Bearer " + token).response();
            assertEquals(403, response.statusCode());
            assertEquals("Bearer realm=\"stride\", error=\"insufficient_scope\", "
                    + "error_description=\"The access token lacks a required scope\", scope=\"read\"", challenge(response));
        }
    }

    @Test
    void withoutAResourceIdentifierNoProtectedResourceMetadataIsPublished() {
        var response = client.get("/.well-known/oauth-protected-resource").response();

        assertEquals(401, response.statusCode());
        assertEquals("Bearer realm=\"stride\"", challenge(response), "and no challenge names a metadata document");
    }

    @Test
    void rolesFromTheTokenDecideRolesAllowed() throws Exception {
        assertEquals("reader", client.get("/reader", "Authorization", "Bearer " + token()).expect200Ok().body());
        assertEquals(403, client.get("/admin", "Authorization", "Bearer " + token()).response().statusCode());
    }

    @Test
    void onlyTheExactPublicPathNeedsNoToken() {
        var open = client.get("/public").response();
        assertEquals(200, open.statusCode());
        assertFalse(open.headers().firstValue("WWW-Authenticate").isPresent());

        assertEquals(401, client.get("/public/child").response().statusCode());
        assertEquals(401, client.get("/public;x=1").response().statusCode());
    }
}
