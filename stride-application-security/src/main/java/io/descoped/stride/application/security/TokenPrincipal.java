package io.descoped.stride.application.security;

import java.security.Principal;
import java.util.Set;

/** Validated identity; never retains the bearer token. */
public record TokenPrincipal(String subject, Set<String> scopes, Set<String> roles) implements Principal {
    public TokenPrincipal {
        scopes = Set.copyOf(scopes);
        roles = Set.copyOf(roles);
    }
    @Override public String getName() { return subject; }
}
