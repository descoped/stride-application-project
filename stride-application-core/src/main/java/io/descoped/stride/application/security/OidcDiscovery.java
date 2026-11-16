package io.descoped.stride.application.security;

import io.descoped.stride.application.config.SecurityConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.util.ResourceRetriever;
import java.io.IOException;
import java.net.URI;

/** Discovery is pinned to operator configuration, never to an untrusted token. */
final class OidcDiscovery {
    private OidcDiscovery() { }

    static URI discover(String issuer, ResourceRetriever retriever) throws IOException {
        SecurityConfiguration.requireHttps(issuer);
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        var resource = retriever.retrieveResource(URI.create(base + "/.well-known/openid-configuration").toURL());
        JsonNode metadata = new ObjectMapper().readTree(resource.getContent());
        if (metadata == null || !issuer.equals(metadata.path("issuer").asText()))
            throw new IOException("OIDC discovery issuer does not match configured issuer");
        try {
            return SecurityConfiguration.requireHttps(metadata.path("jwks_uri").asText());
        } catch (IllegalArgumentException e) {
            throw new IOException("OIDC discovery returned an invalid JWKS endpoint", e);
        }
    }
}
