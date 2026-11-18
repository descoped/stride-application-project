package io.descoped.stride.application.core;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import org.glassfish.hk2.runlevel.RunLevel;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Service;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A named HK2 service found on the class path is switched by {@code services.<name>.enabled}. Without that key,
 * {@code services.hk2.defaults.enabled} decides.
 */
class NamedServiceSwitchTest {

    static final AtomicInteger AUDIT = new AtomicInteger();
    static final AtomicInteger SERVER = new AtomicInteger();

    /** A service the application wrote, with no key of its own in any configuration. */
    @Service(name = "audit")
    @RunLevel(5)
    public static class Audit {
        public Audit() {
            AUDIT.incrementAndGet();
        }
    }

    /** Named as a key that exists deeper in the configuration: services.jetty.server and services.jersey.server. */
    @Service(name = "server")
    @RunLevel(5)
    public static class Server {
        public Server() {
            SERVER.incrementAndGet();
        }
    }

    private static int started(AtomicInteger counter, String... overrides) {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder().testDefaults();
        for (int i = 0; i < overrides.length; i += 2) {
            configuration.overrideProperty(overrides[i], overrides[i + 1]);
        }
        counter.set(0);
        try (StrideApplication application = StrideApplication.create(configuration.build())) {
            application.start();
            return counter.get();
        }
    }

    @Test
    void aNamedServiceWithoutAKeyStartsBecauseTheDefaultIsEnabled() {
        assertEquals(1, started(AUDIT));
    }

    @Test
    void aNamedServiceWithoutAKeyDoesNotStartWhenTheDefaultIsDisabled() {
        assertEquals(0, started(AUDIT, "services.hk2.defaults.enabled", "false"));
    }

    @Test
    void itsOwnKeyDecidesWhateverTheDefaultSays() {
        assertEquals(0, started(AUDIT, "services.audit.enabled", "false"));
        assertEquals(1, started(AUDIT, "services.hk2.defaults.enabled", "false", "services.audit.enabled", "true"));
    }

    @Test
    void aKeyOfTheSameNameElsewhereInTheConfigurationIsNotTheServices() {
        // services.jetty.server.enabled=false stops Jetty, not the application's service named "server"
        assertEquals(1, started(SERVER,
                "services.jetty.server.enabled", "false",
                "services.jetty.start.enabled", "false",
                "services.jersey.server.enabled", "false"));
    }
}
