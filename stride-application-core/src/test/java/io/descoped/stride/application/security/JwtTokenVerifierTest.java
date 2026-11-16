package io.descoped.stride.application.security;

import io.descoped.stride.application.config.SecurityConfiguration;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
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
        return new SecurityConfiguration(true, ISSUER, Set.of("stride-api"), Set.of("RS256"), "at+jwt",
                Set.of("/public"), Set.of("read"), "roles", 0);
    }
    static JwtTokenVerifier verifier() {
        return new JwtTokenVerifier(settings(), new ImmutableJWKSet<>(new JWKSet(KEY.toPublicJWK())));
    }
    static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(ISSUER).audience("stride-api").subject("alice")
                .issueTime(Date.from(Instant.now().minusSeconds(5))).expirationTime(Date.from(Instant.now().plusSeconds(300)))
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
            for (var claimSet : invalid) assertThrows(Exception.class, () -> verifier.verify(token(claimSet)), claimSet.toString());
        }
    }
    @Test void rejectsWrongSignatureUnsignedMalformedAndIdTokens() throws Exception {
        var attacker = new RSAKeyGenerator(2048).keyID(KEY.getKeyID()).generate();
        try (var verifier = verifier()) {
            assertThrows(Exception.class, () -> verifier.verify(token(attacker, claims().build(), "at+jwt")));
            assertThrows(Exception.class, () -> verifier.verify(token(KEY, claims().build(), "JWT")));
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
    @Test void validatesDiscoveryIssuerAndEndpointWithoutTrustingTokenInput() throws Exception {
        var url = new AtomicReference<String>();
        assertEquals(URI.create("https://identity.example.test/keys"), OidcDiscovery.discover(ISSUER, u -> {
            url.set(u.toString());
            return new Resource("{\"issuer\":\"" + ISSUER + "\",\"jwks_uri\":\"https://identity.example.test/keys\"}", "application/json");
        }));
        assertEquals(ISSUER + "/.well-known/openid-configuration", url.get());
        assertThrows(IOException.class, () -> OidcDiscovery.discover(ISSUER, u -> new Resource("{\"issuer\":\"other\"}", "application/json")));
        assertThrows(IOException.class, () -> OidcDiscovery.discover(ISSUER, u -> new Resource("{\"issuer\":\"" + ISSUER + "\",\"jwks_uri\":\"http://insecure/keys\"}", "application/json")));
        assertThrows(IllegalArgumentException.class, () -> SecurityConfiguration.requireHttps("http://insecure"));
        assertThrows(IllegalArgumentException.class, () -> SecurityConfiguration.requireHttps("https://user:password@example.test"));
    }
    @Test void discoveryPreloadsKeysAndFailsClosedWhenProviderIsUnavailable() throws Exception {
        var requests = new AtomicInteger();
        try (var verifier = JwtTokenVerifier.discover(settings(), url -> {
            requests.incrementAndGet();
            if (url.getPath().endsWith("openid-configuration"))
                return new Resource("{\"issuer\":\"" + ISSUER + "\",\"jwks_uri\":\"https://identity.example.test/keys\"}", "application/json");
            return new Resource(new JWKSet(KEY.toPublicJWK()).toString(), "application/json");
        })) {
            assertEquals(2, requests.get());
            verifier.verify(token(claims().build()));
            assertEquals(2, requests.get(), "first authenticated request must not need provider I/O");
        }
        assertThrows(IOException.class, () -> JwtTokenVerifier.discover(settings(), url -> { throw new IOException("provider unavailable"); }));
        assertThrows(IOException.class, () -> JwtTokenVerifier.discover(settings(), url -> {
            if (url.getPath().endsWith("openid-configuration"))
                return new Resource("{\"issuer\":\"" + ISSUER + "\",\"jwks_uri\":\"https://identity.example.test/keys\"}", "application/json");
            return new Resource("{\"keys\":[]}", "application/json");
        }));
    }

    @Test void rejectsUnsafeSettings() {
        assertThrows(IllegalArgumentException.class, () -> new SecurityConfiguration(true, ISSUER, Set.of(), Set.of("RS256"), "at+jwt", Set.of(), Set.of(), "roles", 0));
        assertThrows(IllegalArgumentException.class, () -> new SecurityConfiguration(true, ISSUER, Set.of("api"), Set.of("HS256"), "at+jwt", Set.of(), Set.of(), "roles", 0));
        assertThrows(IllegalArgumentException.class, () -> new SecurityConfiguration(true, ISSUER, Set.of("api"), Set.of("RS256"), "at+jwt", Set.of("/*"), Set.of(), "roles", 0));
    }
}
