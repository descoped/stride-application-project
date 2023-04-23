package io.descoped.stride.application.cors;

import io.descoped.stride.application.config.api.ApplicationConfiguration;
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
        ApplicationCORSServletFilter builder = new Builder().build();
        this.origin = builder.origin;
        this.credentials = builder.credentials;
        this.headers = builder.headers;
        this.methods = builder.methods;
        this.maxAge = builder.maxAge;
    }

    @Override
    public void init() throws ServletException {
        super.init();
    }

    private ApplicationCORSServletFilter(String origin,
                                         String credentials,
                                         String headers,
                                         String methods,
                                         String maxAge) {
        this.origin = origin;
        this.credentials = credentials;
        this.headers = headers;
        this.methods = methods;
        this.maxAge = maxAge;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response,
                            FilterChain chain) throws IOException, ServletException {
        response.addHeader("Access-Control-Allow-Origin", origin);
        response.addHeader("Access-Control-Allow-Credentials", credentials);
        response.addHeader("Access-Control-Allow-Headers", headers);
        response.addHeader("Access-Control-Allow-Methods", methods);
        response.addHeader("Access-Control-Max-Age", maxAge);
        chain.doFilter(request, response);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String origin = "*";
        private String credentials = "true";
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
