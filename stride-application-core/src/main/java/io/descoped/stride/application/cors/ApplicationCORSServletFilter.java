package io.descoped.stride.application.cors;

import io.descoped.stride.application.config.ApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;


public class ApplicationCORSServletFilter extends HttpFilter {

    private final String origin;
    private final String credentials;
    private final String headers;
    private final String methods;
    private final String maxAge;

    @Inject
    public ApplicationCORSServletFilter(ApplicationConfiguration configuration) {
        this(configuration.requireString("filters.cors.config.origin"),
                configuration.requireString("filters.cors.config.credentials"),
                configuration.requireString("filters.cors.config.headers"),
                configuration.requireString("filters.cors.config.methods"),
                configuration.requireString("filters.cors.config.max-age"));
    }

    private ApplicationCORSServletFilter(String origin,
                                         String credentials,
                                         String headers,
                                         String methods,
                                         String maxAge) {
        if ("*".equals(origin) && Boolean.parseBoolean(credentials))
            throw new IllegalArgumentException("Credentialed CORS requires explicit allowed origins");
        this.origin = origin;
        this.credentials = credentials;
        this.headers = headers;
        this.methods = methods;
        this.maxAge = maxAge;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain) throws IOException, ServletException {
        String requestOrigin = request.getHeader("Origin");
        if (requestOrigin == null) {
            chain.doFilter(request, response);
            return;
        }
        response.addHeader("Vary", "Origin");
        boolean allowed = "*".equals(origin) || java.util.Arrays.stream(origin.split(","))
                .map(String::trim).anyMatch(requestOrigin::equals);
        if (!allowed) { response.setStatus(403); return; }
        response.setHeader("Access-Control-Allow-Origin", "*".equals(origin) ? "*" : requestOrigin);
        if (Boolean.parseBoolean(credentials)) response.setHeader("Access-Control-Allow-Credentials", "true");
        if ("OPTIONS".equals(request.getMethod()) && request.getHeader("Access-Control-Request-Method") != null) {
            response.addHeader("Vary", "Access-Control-Request-Method, Access-Control-Request-Headers");
            var allowedMethods = java.util.Arrays.stream(methods.split(",")).map(String::trim).toList();
            var allowedHeaders = java.util.Arrays.stream(headers.split(",")).map(String::trim)
                    .map(v -> v.toLowerCase(java.util.Locale.ROOT)).toList();
            String requestedHeaders = request.getHeader("Access-Control-Request-Headers");
            boolean validHeaders = requestedHeaders == null || java.util.Arrays.stream(requestedHeaders.split(","))
                    .map(String::trim).map(v -> v.toLowerCase(java.util.Locale.ROOT)).allMatch(allowedHeaders::contains);
            if (!allowedMethods.contains(request.getHeader("Access-Control-Request-Method")) || !validHeaders) {
                response.setStatus(403); return;
            }
            response.setHeader("Access-Control-Allow-Headers", headers);
            response.setHeader("Access-Control-Allow-Methods", methods);
            response.setHeader("Access-Control-Max-Age", maxAge);
            response.setStatus(204);
            return;
        }
        chain.doFilter(request, response);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String origin = "*";
        private String credentials = "false";
        private String headers = "origin, content-type, accept, authorization";
        private String methods = "GET, POST, PUT, DELETE, OPTIONS, HEAD";
        private String maxAge = "86400";

        private Builder() {
        }

        public Builder origin(String origin) {
            this.origin = origin;
            return this;
        }

        public Builder credentials(String credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder headers(String headers) {
            this.headers = headers;
            return this;
        }

        public Builder methods(String methods) {
            this.methods = methods;
            return this;
        }

        public Builder maxAge(String maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public ApplicationCORSServletFilter build() {
            return new ApplicationCORSServletFilter(origin, credentials, headers, methods, maxAge);
        }
    }
}
