package io.descoped.stride.sample;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.security.BearerAuthenticationFilter;
import io.descoped.stride.application.security.JwtTokenVerifier;
import io.descoped.stride.application.security.SecurityConfiguration;
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application with authentication on. In production Stride reads the issuer's keys over the network; here
 * the test holds the key pair, hands the application the public half and signs the tokens itself. The filter
 * that checks them is the real one.
 */
class SecuredGreetingApplicationTest {

    private static final String ISSUER = "https://identity.example.test";
    private static final String AUDIENCE = "https://greetings.example.test";
    private static final RSAKey KEY = key();

    @RegisterExtension
    static final StrideExtension stride = StrideExtension.builder()
            .configuration(() -> GreetingApplication.configuration()
                    .overrideProperty("security.enabled", "true")
                    .overrideProperty("security.issuer", ISSUER)
                    .overrideProperty("security.audiences", AUDIENCE))
            .components(configuration -> {
                SecurityConfiguration settings = SecurityConfiguration.from(configuration);
                JwtTokenVerifier verifier = JwtTokenVerifier.withKeys(settings,
                        new ImmutableJWKSet<>(new JWKSet(KEY.toPublicJWK())));
                // the resource is wired as in GreetingApplication.components; the filter is added beside it
                return Components.builder()
                        .resource(new GreetingResource(new Greetings(configuration.requireString("greeting.salutation"))))
                        // an instance named "security" takes the place of the filter Stride would create
                        .filter("security", new BearerAuthenticationFilter(settings, verifier))
                        .build();
            })
            .build();

    @Test
    void theCallerIsGreetedByTheSubjectOfTheToken(TestClient client) throws JOSEException {
        var response = client.get("/greetings", "Authorization", "Bearer " + token("ada")).expect200Ok().response();

        assertEquals("{\"message\":\"Hello, ada!\"}", response.body());
    }

    @Test
    void aRequestWithoutATokenIsChallenged(TestClient client) {
        var response = client.get("/greetings/Ada").expectAnyOf(401).response();

        // RFC 6750 section 3: the challenge names the scheme, and no error when no token was sent
        assertEquals("Bearer realm=\"greetings\"", response.headers().firstValue("WWW-Authenticate").orElseThrow());
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected(TestClient client) throws JOSEException {
        var response = client.get("/greetings", "Authorization", "Bearer " + token("ada", "https://elsewhere.example.test"))
                .expectAnyOf(401).response();

        String challenge = response.headers().firstValue("WWW-Authenticate").orElseThrow();
        assertTrue(challenge.contains("error=\"invalid_token\""), challenge);
    }

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).keyID("test").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String token(String subject) throws JOSEException {
        return token(subject, ISSUER);
    }

    /** An access token as an authorization server issues it (RFC 9068), signed with the test's key. */
    private static String token(String subject, String issuer) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer).audience(AUDIENCE).subject(subject)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                .claim("client_id", "test-client").jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("at+jwt")).keyID(KEY.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }
}
