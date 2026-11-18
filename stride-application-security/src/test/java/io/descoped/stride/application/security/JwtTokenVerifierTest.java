package io.descoped.stride.application.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class JwtTokenVerifierTest {
    static final String ISSUER = "https://identity.example.test/realm";
    static final RSAKey KEY;
    static {
        try { KEY = new RSAKeyGenerator(2048).keyID("first").generate(); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static SecurityConfiguration settings() {
        return settings("at+jwt", "scope");
    }
    static SecurityConfiguration settings(String tokenType, String scopeClaim) {
        return new SecurityConfiguration(true, ISSUER, Set.of("stride-api"), Set.of("RS256"), tokenType,
                Set.of("/public"), Set.of("read"), scopeClaim, "roles", "stride", 0);
    }
    static JwtTokenVerifier verifier(SecurityConfiguration settings) {
        return new JwtTokenVerifier(settings, new ImmutableJWKSet<>(new JWKSet(KEY.toPublicJWK())));
    }
    static JwtTokenVerifier verifier() {
        return verifier(settings());
    }
    static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISSUER).audience("stride-api").subject("alice")
                .issueTime(Date.from(Instant.now().minusSeconds(5))).expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("client_id", "web-client").jwtID(java.util.UUID.randomUUID().toString())
                .claim("scope", "read write").claim("roles", java.util.List.of("reader"));
    }
    static String token(JWTClaimsSet claims) throws Exception { return token(KEY, claims, "at+jwt"); }
    static String token(RSAKey key, JWTClaimsSet claims, String type) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(new JOSEObjectType(type)).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    @Test void acceptsValidIdentityAndAuthorizationClaims() throws Exception {
        try (var verifier = verifier()) {
            var principal = verifier.verify(token(claims().build()));
            assertEquals("alice", principal.getName());
            assertEquals(Set.of("read", "write"), principal.scopes());
            assertEquals(Set.of("reader"), principal.roles());
        }
    }
    @Test void rejectsWrongIssuerAudienceExpiryFutureDatesAndMissingClaims() throws Exception {
        var invalid = java.util.List.of(
                claims().issuer("https://other.example").build(), claims().audience("other").build(),
                claims().expirationTime(Date.from(Instant.now().minusSeconds(60))).build(),
                claims().issueTime(Date.from(Instant.now().plusSeconds(60))).build(),
                claims().notBeforeTime(Date.from(Instant.now().plusSeconds(60))).build(),
                claims().subject(" ").build(), claims().subject(null).build(), claims().expirationTime(null).build(),
                claims().issueTime(null).build(), claims().claim("roles", java.util.List.of(1)).build());
        try (var verifier = verifier()) {
            for (var claimSet : invalid) {
                String token = token(claimSet); // signed outside the lambda: only verify may throw inside it
                assertThrows(Exception.class, () -> verifier.verify(token), claimSet.toString());
            }
        }
    }
    @Test void rejectsWrongSignatureUnsignedMalformedAndIdTokens() throws Exception {
        var attacker = new RSAKeyGenerator(2048).keyID(KEY.getKeyID()).generate();
        // signed outside the lambdas: only verify may throw inside them
        String wrongSignature = token(attacker, claims().build(), "at+jwt");
        String idToken = token(KEY, claims().build(), "JWT");
        try (var verifier = verifier()) {
            assertThrows(Exception.class, () -> verifier.verify(wrongSignature));
            assertThrows(Exception.class, () -> verifier.verify(idToken));
            assertThrows(Exception.class, () -> verifier.verify(new com.nimbusds.jwt.PlainJWT(claims().build()).serialize()));
            assertThrows(Exception.class, () -> verifier.verify("not-a-token"));
            assertThrows(Exception.class, () -> verifier.verify("a".repeat(16_385)));
        }
    }
    @Test void cachesKeysAndRefreshesOnUnknownKey() throws Exception {
        var rotated = new RSAKeyGenerator(2048).keyID("rotated").generate();
        var published = new AtomicReference<>(KEY.toPublicJWK());
        var fetches = new AtomicInteger();
        var source = JWKSourceBuilder.<SecurityContext>create(URI.create("https://identity.example.test/jwks").toURL(), url -> {
            fetches.incrementAndGet();
            return new Resource(new JWKSet(published.get()).toString(), "application/json");
        }).refreshAheadCache(false).rateLimited(false).build();
        try (var verifier = new JwtTokenVerifier(settings(), source)) {
            String first = token(claims().build());
            for (int i = 0; i < 20; i++) verifier.verify(first);
            assertEquals(1, fetches.get(), "steady requests must use cached JWKS");
            published.set(rotated.toPublicJWK());
            assertEquals("alice", verifier.verify(token(rotated, claims().build(), "at+jwt")).getName());
            assertEquals(2, fetches.get());
            assertThrows(Exception.class, () -> verifier.verify(first));
        }
    }
    static Resource metadata(String issuer, String jwksUri) {
        return new Resource("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + jwksUri + "\"}", "application/json");
    }

    @Test void rfc8414Section3_1InsertsTheWellKnownStringBetweenHostAndPath() {
        assertEquals(java.util.List.of(
                        URI.create("https://identity.example.test/.well-known/oauth-authorization-server/realm"),
                        URI.create("https://identity.example.test/realm/.well-known/openid-configuration")),
                IssuerMetadata.locations(ISSUER));
        assertEquals(IssuerMetadata.locations(ISSUER), IssuerMetadata.locations(ISSUER + "/"), "a terminating slash is removed");
        assertEquals(java.util.List.of(
                        URI.create("https://identity.example.test:8443/.well-known/oauth-authorization-server"),
                        URI.create("https://identity.example.test:8443/.well-known/openid-configuration")),
                IssuerMetadata.locations("https://identity.example.test:8443"));
    }

    @Test void rfc8414Section5TriesTheOAuthLocationFirstAndFallsBackToOpenIdDiscovery() throws Exception {
        var requested = new java.util.ArrayList<String>();
        assertEquals(URI.create("https://identity.example.test/keys"), IssuerMetadata.jwksUri(ISSUER, url -> {
            requested.add(url.getPath());
            return metadata(ISSUER, "https://identity.example.test/keys");
        }));
        assertEquals(java.util.List.of("/.well-known/oauth-authorization-server/realm"), requested);

        requested.clear();
        assertEquals(URI.create("https://identity.example.test/oidc-keys"), IssuerMetadata.jwksUri(ISSUER, url -> {
            requested.add(url.getPath());
            if (url.getPath().contains("oauth-authorization-server")) throw new IOException("404");
            return metadata(ISSUER, "https://identity.example.test/oidc-keys");
        }));
        assertEquals(java.util.List.of("/.well-known/oauth-authorization-server/realm", "/realm/.well-known/openid-configuration"), requested);
    }

    @Test void rfc8414Section3_3MetadataWithAnotherIssuerIsNotUsed() {
        // the configured issuer has no trailing slash; "identical" means exactly that
        for (String other : java.util.List.of("https://other.example.test", ISSUER + "/", ISSUER.toUpperCase()))
            assertThrows(IOException.class, () -> IssuerMetadata.jwksUri(ISSUER, url -> metadata(other, "https://identity.example.test/keys")), other);
        assertThrows(IOException.class, () -> IssuerMetadata.jwksUri(ISSUER, url -> new Resource("{\"jwks_uri\":\"https://identity.example.test/keys\"}", "application/json")));
        assertThrows(IOException.class, () -> IssuerMetadata.jwksUri(ISSUER, url -> new Resource("<html>not found</html>", "text/html")));
    }

    @Test void theKeysEndpointMustBeHttpsAndTheIssuerToo() {
        assertThrows(IOException.class, () -> IssuerMetadata.jwksUri(ISSUER, url -> metadata(ISSUER, "http://insecure/keys")));
        assertThrows(IllegalArgumentException.class, () -> SecurityConfiguration.requireHttps("http://insecure"));
        assertThrows(IllegalArgumentException.class, () -> SecurityConfiguration.requireHttps("https://user:password@example.test"));
        assertThrows(IllegalArgumentException.class, () -> SecurityConfiguration.requireHttps("https://example.test?tenant=a"));
    }

    @Test void discoveryPreloadsKeysAndFailsClosedWhenProviderIsUnavailable() throws Exception {
        var requests = new AtomicInteger();
        try (var verifier = JwtTokenVerifier.discover(settings(), url -> {
            requests.incrementAndGet();
            if (url.getPath().contains("/.well-known/")) return metadata(ISSUER, "https://identity.example.test/keys");
            return new Resource(new JWKSet(KEY.toPublicJWK()).toString(), "application/json");
        })) {
            assertEquals(2, requests.get());
            verifier.verify(token(claims().build()));
            assertEquals(2, requests.get(), "first authenticated request must not need provider I/O");
        }
        assertThrows(IOException.class, () -> JwtTokenVerifier.discover(settings(), url -> { throw new IOException("provider unavailable"); }));
        assertThrows(IOException.class, () -> JwtTokenVerifier.discover(settings(), url -> {
            if (url.getPath().contains("/.well-known/")) return metadata(ISSUER, "https://identity.example.test/keys");
            return new Resource("{\"keys\":[]}", "application/json");
        }));
    }

    @Test void rfc9068Section4AcceptsBothSpellingsOfTheAccessTokenTypeAndNoOther() throws Exception {
        try (var verifier = verifier()) {
            assertEquals("alice", verifier.verify(token(KEY, claims().build(), "at+jwt")).getName());
            assertEquals("alice", verifier.verify(token(KEY, claims().build(), "application/at+jwt")).getName());
            assertEquals("alice", verifier.verify(token(KEY, claims().build(), "AT+JWT")).getName(), "media type names are case-insensitive");
            for (String type : java.util.List.of("JWT", "jwt", "id_token+jwt", "at+jwt+x"))
                assertThrows(BadJOSEException.class, () -> verifier.verify(token(KEY, claims().build(), type)), type);
        }
    }

    @Test void rfc9068Section2_2RequiresEveryClaimOfTheProfile() throws Exception {
        try (var verifier = verifier()) {
            for (String claim : java.util.List.of("iss", "exp", "aud", "sub", "client_id", "iat", "jti")) {
                var incomplete = new JWTClaimsSet.Builder(claims().build()).claim(claim, null).build();
                assertThrows(BadJOSEException.class, () -> verifier.verify(token(incomplete)), claim);
            }
        }
    }

    @Test void rfc9068Section4ExpiredTokensAreToldApartFromOtherwiseInvalidOnes() throws Exception {
        try (var verifier = verifier()) {
            var expired = claims().expirationTime(Date.from(Instant.now().minusSeconds(60))).build();
            assertThrows(com.nimbusds.jwt.proc.ExpiredJWTException.class, () -> verifier.verify(token(expired)));
        }
    }

    @Test void tokensOutsideTheProfileNeedTheJwtTokenTypeAndNoProfileClaims() throws Exception {
        var plain = new JWTClaimsSet.Builder(claims().build()).claim("client_id", null).jwtID(null).build();
        try (var verifier = verifier(settings("JWT", "scope"))) {
            assertEquals("alice", verifier.verify(token(KEY, plain, "JWT")).getName());
            assertThrows(BadJOSEException.class, () -> verifier.verify(token(KEY, plain, "at+jwt")));
        }
    }

    @Test void scopesAreReadFromTheConfiguredClaimOnly() throws Exception {
        var entra = new JWTClaimsSet.Builder(claims().build()).claim("scope", null).claim("scp", "read admin").build();
        try (var verifier = verifier()) {
            assertEquals(Set.of(), verifier.verify(token(entra)).scopes(), "no silent fallback to another claim");
        }
        try (var verifier = verifier(settings("at+jwt", "scp"))) {
            assertEquals(Set.of("read", "admin"), verifier.verify(token(entra)).scopes());
        }
    }

    static SecurityConfiguration settings(Set<String> audiences, Set<String> algorithms, Set<String> publicPaths,
                                          Set<String> requiredScopes, String realm) {
        return new SecurityConfiguration(true, ISSUER, audiences, algorithms, "at+jwt", publicPaths, requiredScopes, "scope", "roles", realm, 0);
    }

    // The sets are built before the lambda, so only the constructor of the settings can throw inside it.
    static void assertRejected(Set<String> audiences, Set<String> algorithms, Set<String> publicPaths,
                               Set<String> requiredScopes, String realm) {
        assertThrows(IllegalArgumentException.class, () -> settings(audiences, algorithms, publicPaths, requiredScopes, realm));
    }

    @Test void rejectsUnsafeSettings() {
        assertDoesNotThrow(() -> settings(Set.of("api"), Set.of("RS256"), Set.of("/ping"), Set.of("urn:example:rating=G,PG-13"), "stride api"));
        assertRejected(Set.of(), Set.of("RS256"), Set.of(), Set.of(), "stride");
        assertRejected(Set.of("api"), Set.of("HS256"), Set.of(), Set.of(), "stride");
        assertRejected(Set.of("api"), Set.of("none"), Set.of(), Set.of(), "stride");
        assertRejected(Set.of("api"), Set.of("RS256"), Set.of("/*"), Set.of(), "stride");
        // OAuth 2.1 section 5.3.1: what may appear in the challenge's scope and realm attributes
        assertRejected(Set.of("api"), Set.of("RS256"), Set.of(), Set.of("read\"write"), "stride");
        assertRejected(Set.of("api"), Set.of("RS256"), Set.of(), Set.of("read write"), "stride");
        assertRejected(Set.of("api"), Set.of("RS256"), Set.of(), Set.of(), "my \"realm\"");
        assertRejected(Set.of("api"), Set.of("RS256"), Set.of(), Set.of(), "");
    }
}
