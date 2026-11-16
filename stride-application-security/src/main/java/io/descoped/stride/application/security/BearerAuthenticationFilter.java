package io.descoped.stride.application.security;

import io.descoped.stride.application.config.ApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.proc.ExpiredJWTException;
import java.io.IOException;
import java.security.Principal;
import java.text.ParseException;
import java.util.Collections;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Authenticates every servlet route except explicitly configured exact public paths. */
public final class BearerAuthenticationFilter extends HttpFilter {
    private final SecurityConfiguration settings;
    private final JwtTokenVerifier verifier;

    /**
     * Used when Stride creates the filter from the configuration: reads {@code security.*} and fetches the issuer's
     * metadata and keys, so a wrong or unreachable issuer stops the application before it accepts requests.
     */
    @Inject
    public BearerAuthenticationFilter(ApplicationConfiguration configuration) throws IOException {
        SecurityConfiguration settings = SecurityConfiguration.from(configuration);
        this(settings, JwtTokenVerifier.discover(settings));
    }

    /**
     * For an application that builds the verifier itself and hands the filter to Stride under the name
     * {@code "security"}.
     */
    public BearerAuthenticationFilter(SecurityConfiguration settings, JwtTokenVerifier verifier) {
        this.settings = settings;
        this.verifier = verifier;
    }

    // OAuth 2.1 section 5.1.1: credentials = "bearer" 1*SP token68, the scheme name in any case
    private static final Pattern BEARER_SCHEME = Pattern.compile("(?i)bearer( .*)?");
    private static final Pattern BEARER_CREDENTIALS = Pattern.compile("(?i)bearer +([A-Za-z0-9._~+/-]+=*)");

    /**
     * Accepts the access token only from the Authorization header. A token in the query string is ignored, as
     * OAuth 2.1 section 5.1 requires, and the form-encoded method of section 5.1.2 is not supported.
     */
    @Override protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (settings.publicPaths().contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        var authorization = Collections.list(request.getHeaders("Authorization"));
        if (authorization.size() > 1) {
            reject(response, 400, "invalid_request", "More than one Authorization header", null);
            return;
        }
        // Section 5.3.2: no credentials, or another authentication scheme, gets a challenge without an error code
        if (authorization.isEmpty() || !BEARER_SCHEME.matcher(authorization.getFirst()).matches()) {
            reject(response, 401, null, null, null);
            return;
        }
        Matcher credentials = BEARER_CREDENTIALS.matcher(authorization.getFirst());
        if (!credentials.matches()) {
            reject(response, 400, "invalid_request", "The Authorization header is not a Bearer token", null);
            return;
        }
        TokenPrincipal principal;
        try {
            principal = verifier.verify(credentials.group(1));
        } catch (ExpiredJWTException e) {
            reject(response, 401, "invalid_token", "The access token expired", null);
            return;
        } catch (ParseException | BadJOSEException | JOSEException | IllegalArgumentException e) {
            // RFC 9068 section 4: every failed validation is "invalid_token". The reason is not told to the caller.
            reject(response, 401, "invalid_token", "The access token is invalid", null);
            return;
        }
        if (!principal.scopes().containsAll(settings.requiredScopes())) {
            reject(response, 403, "insufficient_scope", "The access token lacks a required scope",
                    String.join(" ", new TreeSet<>(settings.requiredScopes())));
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public Principal getUserPrincipal() { return principal; }
            @Override public String getRemoteUser() { return principal.getName(); }
            @Override public String getAuthType() { return "Bearer"; }
            @Override public boolean isUserInRole(String role) { return principal.roles().contains(role); }
        }, response);
    }

    /**
     * Answers with the status and a {@code WWW-Authenticate} challenge (OAuth 2.1 section 5.3.1). The scheme must
     * be followed by at least one attribute, so the realm is always there. Every value is a constant or was
     * validated as quotable when the settings were read.
     */
    private void reject(HttpServletResponse response, int status, String error, String description, String scope) {
        StringBuilder challenge = new StringBuilder("Bearer realm=\"").append(settings.realm()).append('"');
        if (error != null) {
            challenge.append(", error=\"").append(error).append("\", error_description=\"").append(description).append('"');
        }
        if (scope != null && !scope.isEmpty()) {
            challenge.append(", scope=\"").append(scope).append('"');
        }
        response.setHeader("WWW-Authenticate", challenge.toString());
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(status);
    }

    @Override public void destroy() {
        try { verifier.close(); }
        catch (IOException e) { throw new IllegalStateException("Could not close JWT key source", e); }
    }
}
