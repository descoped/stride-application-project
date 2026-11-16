package io.descoped.stride.application.security;

import io.descoped.stride.application.config.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import java.io.IOException;
import java.security.Principal;
import java.text.ParseException;
import java.util.Collections;

/** Authenticates every servlet route except explicitly configured exact public paths. */
public final class BearerAuthenticationFilter extends HttpFilter {
    private final SecurityConfiguration settings;
    private final JwtTokenVerifier verifier;

    public BearerAuthenticationFilter(SecurityConfiguration settings, JwtTokenVerifier verifier) {
        this.settings = settings;
        this.verifier = verifier;
    }

    @Override protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (settings.publicPaths().contains(path)) {
            chain.doFilter(request, response);
            return;
        }
        var authorization = Collections.list(request.getHeaders("Authorization"));
        if (authorization.isEmpty()) {
            reject(response, 401, "Bearer");
            return;
        }
        if (authorization.size() != 1 || !authorization.getFirst().regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(response, 401, "Bearer error=\"invalid_token\"");
            return;
        }
        TokenPrincipal principal;
        try {
            principal = verifier.verify(authorization.getFirst().substring(7));
        } catch (ParseException | BadJOSEException | JOSEException | IllegalArgumentException e) {
            reject(response, 401, "Bearer error=\"invalid_token\"");
            return;
        }
        if (!principal.scopes().containsAll(settings.requiredScopes())) {
            reject(response, 403, "Bearer error=\"insufficient_scope\"");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public Principal getUserPrincipal() { return principal; }
            @Override public String getRemoteUser() { return principal.getName(); }
            @Override public String getAuthType() { return "Bearer"; }
            @Override public boolean isUserInRole(String role) { return principal.roles().contains(role); }
        }, response);
    }

    private static void reject(HttpServletResponse response, int status, String challenge) {
        response.setHeader("WWW-Authenticate", challenge);
        response.setHeader("Cache-Control", "no-store");
        response.setStatus(status);
    }

    @Override public void destroy() {
        try { verifier.close(); }
        catch (IOException e) { throw new IllegalStateException("Could not close JWT key source", e); }
    }
}
