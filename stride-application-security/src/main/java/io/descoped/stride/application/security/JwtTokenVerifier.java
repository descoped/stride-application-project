package io.descoped.stride.application.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Reusable verifier with bounded network access, cached JWKS and refresh rate limiting. */
public final class JwtTokenVerifier implements AutoCloseable {
    private final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    private final SecurityConfiguration settings;
    private final JWKSource<SecurityContext> keys;

    /**
     * A verifier that reads the issuer's metadata and keys over the network, as {@code security.issuer} says.
     */
    public static JwtTokenVerifier discover(SecurityConfiguration settings) throws IOException {
        requireEnabled(settings);
        var retriever = new DefaultResourceRetriever(2000, 3000, 1024 * 1024) {
            @Override protected java.net.HttpURLConnection openHTTPConnection(java.net.URL url) throws IOException {
                var connection = super.openHTTPConnection(url);
                connection.setInstanceFollowRedirects(false);
                return connection;
            }
        };
        return discover(settings, retriever);
    }

    static JwtTokenVerifier discover(SecurityConfiguration settings, ResourceRetriever retriever) throws IOException {
        var jwks = IssuerMetadata.jwksUri(settings.issuer(), retriever);
        JWKSource<SecurityContext> keys = JWKSourceBuilder.<SecurityContext>create(jwks.toURL(), retriever)
                .cache(300_000, 5_000).refreshAheadCache(false).rateLimited(30_000).retrying(false).build();
        try {
            if (keys.get(new JWKSelector(new JWKMatcher.Builder().publicOnly(true).build()), null).isEmpty())
                throw new IOException("The key set at " + jwks + " has no public signing keys");
            return new JwtTokenVerifier(settings, keys);
        } catch (KeySourceException | IOException e) {
            if (keys instanceof java.io.Closeable closeable) {
                try { closeable.close(); }
                catch (IOException cleanup) { e.addSuppressed(cleanup); }
            }
            throw new IOException("Could not read the signing keys of issuer " + settings.issuer() + " from " + jwks, e);
        }
    }

    /**
     * A verifier with keys the application supplies itself: a key set it holds, its own way of fetching them, or
     * in a test the public half of a key the test signs tokens with. Nothing is fetched from the issuer; the
     * tokens must still name it in {@code iss}.
     * <p>
     * The verifier owns the key source from here on: {@link #close()} closes it when it is {@code Closeable}.
     * Give the filter the same settings the verifier was built with; the filter takes the realm, scopes and
     * public paths from them, the verifier the issuer, audiences and algorithms.
     */
    public static JwtTokenVerifier withKeys(SecurityConfiguration settings, JWKSource<SecurityContext> keys) {
        requireEnabled(settings);
        return new JwtTokenVerifier(settings, java.util.Objects.requireNonNull(keys, "keys"));
    }

    // SecurityConfiguration validates its values only when it is enabled
    static void requireEnabled(SecurityConfiguration settings) {
        if (!settings.enabled()) {
            throw new IllegalArgumentException("security.enabled is false, so the security settings are not validated "
                    + "and cannot be used to authenticate: set security.enabled=true, or leave the security filter out "
                    + "(filters.security.enabled=false)");
        }
    }

    JwtTokenVerifier(SecurityConfiguration settings, JWKSource<SecurityContext> keys) {
        this.settings = settings;
        this.keys = keys;
        Set<JWSAlgorithm> algorithms = settings.algorithms().stream().map(JWSAlgorithm::parse).collect(Collectors.toUnmodifiableSet());
        // RFC 9068 section 4: "typ" is "at+jwt" or "application/at+jwt", and any other value is rejected. That is
        // also what keeps an OpenID Connect ID token from being accepted as an access token (section 5).
        boolean profile = SecurityConfiguration.ACCESS_TOKEN_TYPE.equals(settings.tokenType());
        processor.setJWSTypeVerifier(profile
                ? new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt"), new JOSEObjectType("application/at+jwt"))
                : new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType(settings.tokenType())));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(algorithms, keys));
        var claims = new DefaultJWTClaimsVerifier<SecurityContext>(settings.audiences(),
                new JWTClaimsSet.Builder().issuer(settings.issuer()).build(),
                // RFC 9068 section 2.2 lists the claims an access token of the profile must carry
                profile ? Set.of("iss", "exp", "aud", "sub", "client_id", "iat", "jti") : Set.of("iss", "exp", "aud", "sub", "iat"),
                Set.of());
        claims.setMaxClockSkew(settings.clockSkewSeconds());
        processor.setJWTClaimsSetVerifier(claims);
    }

    public TokenPrincipal verify(String token) throws ParseException, BadJOSEException, JOSEException {
        if (token == null || token.length() > 16_384) throw new BadJWTException("Invalid token length");
        JWTClaimsSet claims = processor.process(token, null);
        if (claims.getSubject().isBlank() || claims.getIssueTime().toInstant().isAfter(Instant.now().plusSeconds(settings.clockSkewSeconds())))
            throw new BadJWTException("Invalid subject or issue time");
        return new TokenPrincipal(claims.getSubject(), strings(claims.getClaim(settings.scopeClaim())),
                strings(claims.getClaim(settings.rolesClaim())));
    }

    private static Set<String> strings(Object claim) throws BadJWTException {
        if (claim == null) return Set.of();
        if (claim instanceof String value) // a space-delimited list, as "scope" is defined (RFC 8693 section 4.2)
            return Arrays.stream(value.split(" ")).filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
        if (claim instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance))
            return values.stream().map(String.class::cast).collect(Collectors.toUnmodifiableSet());
        throw new BadJWTException("Invalid authorization claim");
    }

    @Override public void close() throws IOException {
        if (keys instanceof java.io.Closeable closeable) closeable.close();
    }
}
