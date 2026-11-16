package io.descoped.stride.application.server;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.SecurityConfiguration;
import io.descoped.stride.application.security.BearerAuthenticationFilter;
import io.descoped.stride.application.security.JwtTokenVerifier;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.glassfish.hk2.api.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every servlet filter of the application, in the order they run: ascending order, then name.
 */
final class FilterRegistrations {

    private static final Logger log = LoggerFactory.getLogger(FilterRegistrations.class);

    record Registration(String name, int order, Filter filter, String pathSpec, EnumSet<DispatcherType> dispatches) {
    }

    /** Authentication runs after CORS (100), so preflight and rejected requests still get CORS headers. */
    static final int AUTHENTICATION_ORDER = 200;

    private FilterRegistrations() {
    }

    /**
     * Collects the filters in the configuration, created from their class unless the application handed in an
     * instance of that name; instances without a configuration entry; and authentication when enabled.
     */
    static List<Registration> collect(ApplicationConfiguration configuration, Components components,
                                      ServiceLocator serviceLocator) {
        List<Registration> registrations = new ArrayList<>();
        Set<String> configured = new HashSet<>();
        for (io.descoped.stride.application.config.Filter filter : configuration.filters().iterator()) {
            configured.add(filter.name());
            Filter instance = components.filters().get(filter.name());
            if (instance == null) {
                if (!filter.isEnabled()) continue;
                if (filter.className() == null) {
                    throw new IllegalStateException("Filter '" + filter.name() + "' is enabled but has neither "
                            + "filters." + filter.name() + ".config.class nor an instance in Components");
                }
                Class<? extends Filter> filterClass = filter.clazz();
                instance = serviceLocator.createAndInitialize(filterClass);
            } else if (!filter.isEnabled() && filter.json().has("enabled")) {
                // code decides which components exist; an instance is never dropped by configuration
                log.warn("Filter '{}' was handed in as an instance and is registered although filters.{}.enabled is false",
                        filter.name(), filter.name());
            }
            registrations.add(new Registration(filter.name(), filter.order(), instance, filter.pathSpec(), filter.dispatches()));
        }
        components.filters().forEach((name, instance) -> {
            if (!configured.contains(name)) {
                registrations.add(new Registration(name, io.descoped.stride.application.config.Filter.DEFAULT_ORDER, instance,
                        io.descoped.stride.application.config.Filter.DEFAULT_PATH_SPEC, EnumSet.of(DispatcherType.REQUEST)));
            }
        });

        SecurityConfiguration security = configuration.security();
        if (security.enabled()) {
            JwtTokenVerifier verifier;
            try {
                verifier = JwtTokenVerifier.discover(security);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read the issuer's metadata or keys", e);
            }
            registrations.add(new Registration("security", AUTHENTICATION_ORDER,
                    new BearerAuthenticationFilter(security, verifier),
                    io.descoped.stride.application.config.Filter.DEFAULT_PATH_SPEC, EnumSet.allOf(DispatcherType.class)));
        }

        registrations.sort(Comparator.comparingInt(Registration::order).thenComparing(Registration::name));
        return registrations;
    }
}
