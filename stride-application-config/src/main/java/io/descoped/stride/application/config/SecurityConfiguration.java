package io.descoped.stride.application.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable, validated resource-server settings. Authentication is opt-in. */
public record SecurityConfiguration(boolean enabled, String issuer, Set<String> audiences,
                               Set<String> algorithms, String tokenType, Set<String> publicPaths,
                               Set<String> requiredScopes, String rolesClaim, int clockSkewSeconds) {
    public SecurityConfiguration {
        audiences = Set.copyOf(audiences);
        algorithms = Set.copyOf(algorithms);
        publicPaths = Set.copyOf(publicPaths);
        requiredScopes = Set.copyOf(requiredScopes);
        if (enabled) {
            requireHttps(issuer);
            if (audiences.isEmpty()) throw new IllegalArgumentException("security.audiences is required");
            if (algorithms.isEmpty() || !Set.of("RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512").containsAll(algorithms))
                throw new IllegalArgumentException("security.algorithms must contain supported asymmetric algorithms");
            if (!Set.of("at+jwt", "JWT").contains(tokenType))
                throw new IllegalArgumentException("security.token-type must be at+jwt or JWT");
            if (clockSkewSeconds < 0 || clockSkewSeconds > 300)
                throw new IllegalArgumentException("security.clock-skew-seconds must be between 0 and 300");
            for (String path : publicPaths) {
                if (!path.startsWith("/") || path.contains("*") || path.contains(";") || path.contains("%") || path.contains(".."))
                    throw new IllegalArgumentException("security.public-paths must contain exact application paths");
            }
        }
    }

    public static SecurityConfiguration from(ApplicationConfiguration config) {
        return new SecurityConfiguration(config.asBoolean("security.enabled", false),
                config.asString("security.issuer", ""), csv(config.asString("security.audiences", "")),
                csv(config.asString("security.algorithms", "RS256")), config.asString("security.token-type", "at+jwt"),
                csv(config.asString("security.public-paths", "")), csv(config.asString("security.required-scopes", "")),
                config.asString("security.roles-claim", "roles"), config.asInt("security.clock-skew-seconds", 30));
    }

    static Set<String> csv(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public static URI requireHttps(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Issuer and JWKS endpoints must be absolute HTTPS URLs without credentials, query or fragment");
        return uri;
    }
}
