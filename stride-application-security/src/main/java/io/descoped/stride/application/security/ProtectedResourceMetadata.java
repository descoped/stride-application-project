package io.descoped.stride.application.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The OAuth 2.0 protected resource metadata of this resource server (RFC 9728): the document a client reads to
 * learn which authorization server issues tokens for it. Published only when {@code security.resource} is set.
 */
final class ProtectedResourceMetadata {

    /** RFC 9728 section 3: the default well-known URI string. */
    static final String WELL_KNOWN = "/.well-known/oauth-protected-resource";

    private final String path;
    private final String query;
    private final String location;
    private final byte[] document;

    private ProtectedResourceMetadata(SecurityConfiguration settings) {
        URI resource = URI.create(settings.resource());
        this.path = WELL_KNOWN + pathOf(resource);
        this.query = resource.getRawQuery();
        this.location = location(resource).toString();
        this.document = document(settings);
    }

    /** The metadata of the configured resource, or null when no resource identifier is configured. */
    static ProtectedResourceMetadata of(SecurityConfiguration settings) {
        return settings.resource().isEmpty() ? null : new ProtectedResourceMetadata(settings);
    }

    /**
     * RFC 9728 section 3.1: the well-known string is inserted between the host and the path and query of the
     * resource identifier, after removing a slash that ends the identifier right after the host.
     */
    static URI location(URI resource) {
        String query = resource.getRawQuery() == null ? "" : "?" + resource.getRawQuery();
        return URI.create("https://" + resource.getRawAuthority() + WELL_KNOWN + pathOf(resource) + query);
    }

    private static String pathOf(URI resource) {
        String path = resource.getRawPath() == null ? "" : resource.getRawPath();
        return path.equals("/") ? "" : path;
    }

    /**
     * RFC 9728 section 2 and 3.2. {@code resource} is the configured identifier, character for character, because
     * a client must find it identical to the one it started from (section 3.3). A parameter without a value is
     * left out. The token is accepted from the Authorization header only, so that is the one bearer method.
     */
    private static byte[] document(SecurityConfiguration settings) {
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        metadata.put("resource", settings.resource());
        metadata.putArray("authorization_servers").add(settings.issuer());
        if (!settings.requiredScopes().isEmpty()) {
            var scopes = metadata.putArray("scopes_supported");
            new TreeSet<>(settings.requiredScopes()).forEach(scopes::add);
        }
        metadata.putArray("bearer_methods_supported").add("header");
        return metadata.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Whether a request for this path inside the application, with this query string, asks for the metadata. */
    boolean isAt(String requestPath, String requestQuery) {
        return path.equals(requestPath) && Objects.equals(query, requestQuery);
    }

    /** The URL of the metadata, for the {@code resource_metadata} attribute of a challenge (section 5.1). */
    String location() {
        return location;
    }

    /** The JSON document, UTF-8 encoded. */
    byte[] document() {
        return document.clone();
    }
}
