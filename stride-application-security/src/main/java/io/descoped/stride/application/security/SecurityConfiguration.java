package io.descoped.stride.application.security;

import io.descoped.stride.application.config.ApplicationConfiguration;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Immutable, validated settings of a resource server. Authentication is opt-in.
 *
 * @param issuer         the authorization server's issuer identifier (RFC 8414 section 2); a token's {@code iss}
 *                       must match it exactly
 * @param audiences      the identifiers this resource server expects for itself; a token's {@code aud} must contain
 *                       one of them
 * @param tokenType      {@code at+jwt}: tokens follow the JWT profile for access tokens (RFC 9068). {@code JWT}:
 *                       tokens from an authorization server that does not follow the profile
 * @param requiredScopes scopes every request needs
 * @param scopeClaim     the claim holding the granted scopes; {@code scope} per RFC 9068 section 2.2.3
 * @param rolesClaim     the claim holding roles; {@code roles} per RFC 9068 section 2.2.3.1
 * @param realm          the {@code realm} of the {@code WWW-Authenticate} challenge
 * @param resource       this resource server's resource identifier (RFC 9728 section 1.2), an https URL without a
 *                       fragment; empty when no protected resource metadata is published
 */
public record SecurityConfiguration(boolean enabled, String issuer, Set<String> audiences,
                                    Set<String> algorithms, String tokenType, Set<String> publicPaths,
                                    Set<String> requiredScopes, String scopeClaim, String rolesClaim,
                                    String realm, int clockSkewSeconds, String resource) {

    /** The token type of the JWT profile for access tokens (RFC 9068 section 2.1). */
    public static final String ACCESS_TOKEN_TYPE = "at+jwt";

    private static final Set<String> ASYMMETRIC_ALGORITHMS =
            Set.of("RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512");

    // OAuth 2.1 section 5.3.1: the characters allowed in a scope value, and in a quoted challenge attribute
    private static final Pattern SCOPE_TOKEN = Pattern.compile("[\\x21\\x23-\\x5B\\x5D-\\x7E]+");
    private static final Pattern QUOTED_TEXT = Pattern.compile("[\\x20\\x21\\x23-\\x5B\\x5D-\\x7E]+");

    /** Settings of a resource server that publishes no protected resource metadata. */
    public SecurityConfiguration(boolean enabled, String issuer, Set<String> audiences,
                                 Set<String> algorithms, String tokenType, Set<String> publicPaths,
                                 Set<String> requiredScopes, String scopeClaim, String rolesClaim,
                                 String realm, int clockSkewSeconds) {
        this(enabled, issuer, audiences, algorithms, tokenType, publicPaths, requiredScopes, scopeClaim, rolesClaim,
                realm, clockSkewSeconds, "");
    }

    public SecurityConfiguration {
        resource = resource == null ? "" : resource;
        audiences = Set.copyOf(audiences);
        algorithms = Set.copyOf(algorithms);
        publicPaths = Set.copyOf(publicPaths);
        requiredScopes = Set.copyOf(requiredScopes);
        if (enabled) {
            requireHttps(issuer);
            if (audiences.isEmpty()) {
                throw new IllegalArgumentException("security.audiences is required");
            }
            requireAsymmetric(algorithms);
            if (!Set.of(ACCESS_TOKEN_TYPE, "JWT").contains(tokenType)) {
                throw new IllegalArgumentException("security.token-type must be at+jwt or JWT");
            }
            if (clockSkewSeconds < 0 || clockSkewSeconds > 300) {
                throw new IllegalArgumentException("security.clock-skew-seconds must be between 0 and 300");
            }
            requireExactPaths(publicPaths);
            requireScopeTokens(requiredScopes);
            requireClaimName("security.scope-claim", scopeClaim);
            requireClaimName("security.roles-claim", rolesClaim);
            requireQuotableRealm(realm);
            if (!resource.isEmpty()) {
                requireResourceIdentifier(resource);
            }
        }
    }

    private static void requireAsymmetric(Set<String> algorithms) {
        if (algorithms.isEmpty() || !ASYMMETRIC_ALGORITHMS.containsAll(algorithms)) {
            throw new IllegalArgumentException("security.algorithms must contain supported asymmetric algorithms");
        }
    }

    private static void requireExactPaths(Set<String> publicPaths) {
        for (String path : publicPaths) {
            if (!path.startsWith("/") || path.contains("*") || path.contains(";") || path.contains("%") || path.contains("..")) {
                throw new IllegalArgumentException("security.public-paths must contain exact application paths");
            }
        }
    }

    private static void requireScopeTokens(Set<String> requiredScopes) {
        for (String scope : requiredScopes) {
            if (!SCOPE_TOKEN.matcher(scope).matches()) {
                throw new IllegalArgumentException("security.required-scopes holds a value that is not a scope: '" + scope + "'");
            }
        }
    }

    private static void requireClaimName(String key, String claim) {
        if (claim == null || claim.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
    }

    private static void requireQuotableRealm(String realm) {
        if (realm == null || !QUOTED_TEXT.matcher(realm).matches()) {
            throw new IllegalArgumentException("security.realm is required and cannot contain a quote, a backslash "
                    + "or characters outside printable ASCII");
        }
    }

    public static SecurityConfiguration from(ApplicationConfiguration config) {
        return new SecurityConfiguration(config.requireBoolean("security.enabled"),
                config.requireString("security.issuer"), csv(config.requireString("security.audiences")),
                csv(config.requireString("security.algorithms")), config.requireString("security.token-type"),
                csv(config.requireString("security.public-paths")), scopes(config.requireString("security.required-scopes")),
                config.requireString("security.scope-claim"), config.requireString("security.roles-claim"),
                config.requireString("security.realm"), config.requireInt("security.clock-skew-seconds"),
                config.requireString("security.resource"));
    }

    static Set<String> csv(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** A scope list is delimited by spaces (OAuth 2.1 section 1.4.1); a scope value may itself contain a comma. */
    static Set<String> scopes(String value) {
        return Arrays.stream(value.split(" ")).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * RFC 9728 section 1.2: a resource identifier is a URL that uses the https scheme and has no fragment. A query
     * is allowed. The identifier is quoted in a challenge, so it must also be quotable.
     */
    private static void requireResourceIdentifier(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("security.resource is not a URL: '" + value + "'", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || !QUOTED_TEXT.matcher(value).matches()) {
            throw new IllegalArgumentException("security.resource must be an absolute https URL without credentials "
                    + "or fragment: '" + value + "'");
        }
    }

    public static URI requireHttps(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Issuer and JWKS endpoints must be absolute HTTPS URLs without credentials, query or fragment");
        }
        return uri;
    }
}
