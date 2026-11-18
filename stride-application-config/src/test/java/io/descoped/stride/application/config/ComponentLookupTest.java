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
            .filters(Filters.builder().filter(Filter.builder("audit.requests").className("com.example.AuditFilter").pathSpec("/*")))
            .servlets(Servlets.builder().servlet(Servlet.builder("admin").pathSpec("/admin/*"))
                    .servlet(Servlet.builder("reports.daily").pathSpec("/reports/daily")))
            .resources(Resources.builder().resource(Resource.builder("greeting").clazz(String.class))
                    .resource(Resource.builder("shop.orders").clazz(String.class)))
            .build();

    @Test
    void aDeclaredComponentIsFoundByItsName() {
        assertEquals("orders", CONFIGURATION.services().service("orders").orElseThrow().name());
        assertEquals("jetty.server", CONFIGURATION.services().service("jetty.server").orElseThrow().name());
        assertEquals("cors", CONFIGURATION.filters().filter("cors").orElseThrow().name());
        assertEquals("/admin/*", CONFIGURATION.servlets().servlet("admin").orElseThrow().pathSpec());
        assertEquals("greeting", CONFIGURATION.resources().resource("greeting").orElseThrow().name());
        assertEquals("audit.requests", CONFIGURATION.filters().filter("audit.requests").orElseThrow().name());
        assertEquals("reports.daily", CONFIGURATION.servlets().servlet("reports.daily").orElseThrow().name());
        assertEquals("shop.orders", CONFIGURATION.resources().resource("shop.orders").orElseThrow().name());
    }

    @Test
    void aNameThatIsNotDeclaredGivesNothing() {
        assertTrue(CONFIGURATION.services().service("nosuch").isEmpty());
        assertTrue(CONFIGURATION.services().service("jetty.nosuch").isEmpty());
        assertTrue(CONFIGURATION.services().service("jetty").isEmpty(), "the parent of jetty.server is not a service");
        assertTrue(CONFIGURATION.services().service("jersey").isEmpty());
        assertTrue(CONFIGURATION.filters().filter("nosuch").isEmpty());
        assertTrue(CONFIGURATION.servlets().servlet("nosuch").isEmpty());
        assertTrue(CONFIGURATION.resources().resource("nosuch").isEmpty());
    }

    @Test
    void theParentOfADottedNameIsNotAComponent() {
        assertTrue(CONFIGURATION.filters().filter("audit").isEmpty());
        assertTrue(CONFIGURATION.servlets().servlet("reports").isEmpty());
        assertTrue(CONFIGURATION.resources().resource("shop").isEmpty());
        // nor is a part of a component
        assertTrue(CONFIGURATION.services().service("jetty.server.config").isEmpty());
    }
}
