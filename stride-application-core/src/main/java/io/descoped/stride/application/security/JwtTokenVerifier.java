package io.descoped.stride.application.security;

import io.descoped.stride.application.config.SecurityConfiguration;
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

    public static JwtTokenVerifier discover(SecurityConfiguration settings) throws IOException {
        if (!settings.enabled()) throw new IllegalArgumentException("Security must be enabled");
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
        var jwks = OidcDiscovery.discover(settings.issuer(), retriever);
        JWKSource<SecurityContext> keys = JWKSourceBuilder.<SecurityContext>create(jwks.toURL(), retriever)
                .cache(300_000, 5_000).refreshAheadCache(false).rateLimited(30_000).retrying(false).build();
        try {
            if (keys.get(new JWKSelector(new JWKMatcher.Builder().publicOnly(true).build()), null).isEmpty())
                throw new IOException("OIDC provider has no public signing keys");
            return new JwtTokenVerifier(settings, keys);
        } catch (KeySourceException | IOException e) {
            if (keys instanceof java.io.Closeable closeable) {
                try { closeable.close(); }
                catch (IOException cleanup) { e.addSuppressed(cleanup); }
            }
            throw new IOException("Could not initialize OIDC signing keys", e);
        }
    }

    JwtTokenVerifier(SecurityConfiguration settings, JWKSource<SecurityContext> keys) {
        this.settings = settings;
        this.keys = keys;
        Set<JWSAlgorithm> algorithms = settings.algorithms().stream().map(JWSAlgorithm::parse).collect(Collectors.toUnmodifiableSet());
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType(settings.tokenType())));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(algorithms, keys));
        var claims = new DefaultJWTClaimsVerifier<SecurityContext>(settings.audiences(),
                new JWTClaimsSet.Builder().issuer(settings.issuer()).build(), Set.of("iss", "aud", "sub", "exp", "iat"), Set.of());
        claims.setMaxClockSkew(settings.clockSkewSeconds());
        processor.setJWTClaimsSetVerifier(claims);
    }

    public TokenPrincipal verify(String token) throws ParseException, BadJOSEException, JOSEException {
        if (token == null || token.length() > 16_384) throw new BadJWTException("Invalid token length");
        JWTClaimsSet claims = processor.process(token, null);
        if (claims.getSubject().isBlank() || claims.getIssueTime().toInstant().isAfter(Instant.now().plusSeconds(settings.clockSkewSeconds())))
            throw new BadJWTException("Invalid subject or issue time");
        Set<String> scopes = strings(claims.getClaim("scope"));
        if (scopes.isEmpty()) scopes = strings(claims.getClaim("scp"));
        return new TokenPrincipal(claims.getSubject(), scopes, strings(claims.getClaim(settings.rolesClaim())));
    }

    private static Set<String> strings(Object claim) throws BadJWTException {
        if (claim == null) return Set.of();
        if (claim instanceof String value)
            return Arrays.stream(value.split("\\s+")).filter(s -> !s.isBlank()).collect(Collectors.toUnmodifiableSet());
        if (claim instanceof Collection<?> values && values.stream().allMatch(String.class::isInstance))
            return values.stream().map(String.class::cast).collect(Collectors.toUnmodifiableSet());
        throw new BadJWTException("Invalid authorization claim");
    }

    @Override public void close() throws IOException {
        if (keys instanceof java.io.Closeable closeable) closeable.close();
    }
}
