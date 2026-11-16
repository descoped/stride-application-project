package io.descoped.stride.application.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.util.ResourceRetriever;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Finds the issuer's signing keys from its published metadata. The issuer comes from the operator's configuration,
 * never from a token.
 */
final class IssuerMetadata {

    private IssuerMetadata() {
    }

    /**
     * The metadata locations of an issuer, in the order to try them (RFC 8414 section 5): first the well-known
     * string inserted between host and path (RFC 8414 section 3.1), then appended to the issuer (OpenID Connect
     * Discovery 1.0 section 4). Without a path component the two differ only in the well-known name.
     */
    static List<URI> locations(String issuer) {
        URI identifier = SecurityConfiguration.requireHttps(issuer);
        String origin = "https://" + identifier.getRawAuthority();
        String path = identifier.getRawPath() == null ? "" : identifier.getRawPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1); // RFC 8414 section 3.1: a terminating "/" is removed
        }
        return List.of(
                URI.create(origin + "/.well-known/oauth-authorization-server" + path),
                URI.create(origin + path + "/.well-known/openid-configuration"));
    }

    /**
     * Returns the issuer's {@code jwks_uri}. A document is used only when its {@code issuer} is identical to the
     * configured one (RFC 8414 section 3.3); otherwise the next location is tried.
     */
    static URI jwksUri(String issuer, ResourceRetriever retriever) throws IOException {
        IOException failure = new IOException("No usable authorization server metadata for issuer " + issuer);
        for (URI location : locations(issuer)) {
            try {
                JsonNode metadata = new ObjectMapper().readTree(retriever.retrieveResource(location.toURL()).getContent());
                if (metadata == null || !metadata.path("issuer").isTextual()
                        || !issuer.equals(metadata.path("issuer").asText())) {
                    throw new IOException("The issuer in " + location + " is not identical to the configured issuer");
                }
                return SecurityConfiguration.requireHttps(metadata.path("jwks_uri").asText());
            } catch (IOException | IllegalArgumentException e) {
                failure.addSuppressed(e);
            }
        }
        throw failure;
    }
}
