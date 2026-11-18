package io.descoped.stride.application.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Looking a component up by name gives it when it is declared, and nothing when it is not.
 */
class ComponentLookupTest {

    private static final ApplicationConfiguration CONFIGURATION = ApplicationConfiguration.builder().testDefaults()
            .services(Services.builder().service(Service.builder("orders").clazz(String.class).runLevel(5)))
            .servlets(Servlets.builder().servlet(Servlet.builder("admin").pathSpec("/admin/*")))
            .resources(Resources.builder().resource(Resource.builder("greeting").clazz(String.class)))
            .build();

    @Test
    void aDeclaredComponentIsFoundByItsName() {
        assertEquals("orders", CONFIGURATION.services().service("orders").orElseThrow().name());
        assertEquals("jetty.server", CONFIGURATION.services().service("jetty.server").orElseThrow().name());
        assertEquals("cors", CONFIGURATION.filters().filter("cors").orElseThrow().name());
        assertEquals("/admin/*", CONFIGURATION.servlets().servlet("admin").orElseThrow().pathSpec());
        assertEquals("greeting", CONFIGURATION.resources().resource("greeting").orElseThrow().name());
    }

    @Test
    void aNameThatIsNotDeclaredGivesNothing() {
        assertTrue(CONFIGURATION.services().service("nosuch").isEmpty());
        assertTrue(CONFIGURATION.services().service("jetty.nosuch").isEmpty());
        assertTrue(CONFIGURATION.filters().filter("nosuch").isEmpty());
        assertTrue(CONFIGURATION.servlets().servlet("nosuch").isEmpty());
        assertTrue(CONFIGURATION.resources().resource("nosuch").isEmpty());
    }
}
