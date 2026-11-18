package io.descoped.stride.application.core;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.config.Servlet;
import io.descoped.stride.application.config.Servlets;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.inject.Inject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An application wires its own objects with constructors and hands them to Stride. Nothing here is created by HK2
 * unless the test says so.
 */
class ForwardPassingTest {

    /** A collaborator with no annotations and no default constructor. */
    record Prices(Map<String, Integer> byItem) {
        int of(String item) {
            return byItem.getOrDefault(item, -1);
        }
    }

    @Path("/price")
    public static class PriceResource {
        private final Prices prices;

        PriceResource(Prices prices) {
            this.prices = prices;
        }

        @GET
        @Path("/{item}")
        @Produces(MediaType.TEXT_PLAIN)
        public String price(@PathParam("item") String item) {
            return String.valueOf(prices.of(item));
        }
    }

    /** Created by Jersey from its class; receives the instance the application registered. */
    @Path("/injected")
    public static class InjectedResource {
        private final Prices prices;

        @Inject
        public InjectedResource(Prices prices) {
            this.prices = prices;
        }

        @GET
        @Path("/{item}")
        @Produces(MediaType.TEXT_PLAIN)
        public String price(@PathParam("item") String item) {
            return String.valueOf(prices.of(item));
        }
    }

    /** Appends its label to a response header, so the header shows the order the filters ran in. */
    static class Marker extends HttpFilter {
        private final String label;

        Marker(String label) {
            this.label = label;
        }

        @Override
        protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            response.addHeader("X-Ran", label);
            chain.doFilter(request, response);
        }
    }

    static class HelloServlet extends HttpServlet {
        private final String greeting;

        HelloServlet(String greeting) {
            this.greeting = greeting;
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.getWriter().print(greeting);
        }
    }

    private static final Prices PRICES = new Prices(Map.of("tea", 30));

    private static TestApplication start(ApplicationConfiguration.Builder configuration, Components components) {
        TestApplication application = new TestApplication.Builder()
                .configuration(configuration)
                .components(components)
                .build();
        application.start();
        return application;
    }

    @Test
    void resourceInstanceWiredByHandServesRequests() {
        Components components = Components.builder().resource(new PriceResource(PRICES)).build();

        try (TestApplication application = start(ApplicationConfiguration.builder().testDefaults(), components)) {
            assertEquals("30", TestClient.create(application).get("/price/tea").expect200Ok().body());
        }
    }

    @Test
    void serviceInstanceIsInjectedIntoAResourceCreatedFromItsClass() {
        Components components = Components.builder().service(Prices.class, PRICES).build();
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .resources(Resources.builder().resource(Resource.builder("injected").clazz(InjectedResource.class)));

        try (TestApplication application = start(configuration, components)) {
            assertEquals("30", TestClient.create(application).get("/injected/tea").expect200Ok().body());
            assertEquals(PRICES, application.getServiceLocator().getService(Prices.class));
        }
    }

    @Test
    void filterInstancesRunInAscendingOrderThenByName() {
        Components components = Components.builder()
                .resource(new PriceResource(PRICES))
                .filter("zebra", new Marker("zebra"))      // no configuration entry: order 1000
                .filter("apple", new Marker("apple"))      // no configuration entry: order 1000, before zebra by name
                .filter("first", new Marker("first"))
                .build();
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .filters(Filters.builder().filter(Filter.builder("first").order(50)));

        try (TestApplication application = start(configuration, components)) {
            List<String> ran = TestClient.create(application).get("/price/tea").expect200Ok()
                    .response().headers().allValues("X-Ran");

            assertEquals(List.of("first", "apple", "zebra"), ran);
        }
    }

    @Test
    void corsRunsBeforeApplicationFiltersAndAFilterCanBePlacedAheadOfIt() {
        // CORS answers a preflight itself, so a filter after it never sees the request
        Components components = Components.builder()
                .filter("before", new Marker("before"))
                .filter("after", new Marker("after"))
                .build();
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .filters(Filters.builder().filter(Filter.builder("before").order(10)));

        try (TestApplication application = start(configuration, components)) {
            var response = TestClient.create(application).options("/price/tea",
                    "Origin", "https://app.example.test", "Access-Control-Request-Method", "GET").response();

            assertEquals(List.of("before"), response.headers().allValues("X-Ran"));
            assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isPresent());
        }
    }

    @Test
    void anInstanceIsRegisteredEvenWhenTheConfigurationDisablesItsName() {
        Components components = Components.builder()
                .resource(new PriceResource(PRICES))
                .filter("guard", new Marker("guard"))
                .build();
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .overrideProperty("filters.guard.enabled", "false");

        try (TestApplication application = start(configuration, components)) {
            assertEquals(List.of("guard"), TestClient.create(application).get("/price/tea").expect200Ok()
                    .response().headers().allValues("X-Ran"));
        }
    }

    @Test
    void systemPropertiesTuneAnInstanceThatIsDeclaredWithItsBuilder() {
        Components components = Components.builder()
                .resource(new PriceResource(PRICES))
                .filter("scoped", new Marker("scoped"))
                .filter("early", new Marker("early"))
                .filter("late", new Marker("late"))
                .build();
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .filters(Filters.builder()
                        .filter(Filter.builder("scoped"))
                        .filter(Filter.builder("early"))
                        .filter(Filter.builder("late").order(5)));

        System.setProperty("filters.scoped.config.pathSpec", "/price/*");
        System.setProperty("filters.late.config.order", "2000");
        try (TestApplication application = start(configuration, components)) {
            TestClient client = TestClient.create(application);

            assertEquals(List.of("early", "scoped", "late"),
                    client.get("/price/tea").expect200Ok().response().headers().allValues("X-Ran"));
            assertEquals(List.of("early", "late"), client.get("/ping").expect200Ok().response().headers().allValues("X-Ran"));
        } finally {
            System.clearProperty("filters.scoped.config.pathSpec");
            System.clearProperty("filters.late.config.order");
        }
    }

    @Test
    void filtersApplyWhenTheJerseyServiceIsDisabled() {
        Components components = Components.builder().filter("guard", new Marker("guard")).build();
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .overrideProperty("services.jersey.server.enabled", "false");

        try (TestApplication application = start(configuration, components)) {
            var ping = TestClient.create(application).get("/ping").expect200Ok().response();

            assertEquals(List.of("guard"), ping.headers().allValues("X-Ran"));
        }
    }

    @Test
    void anOrderThatIsNotANumberFailsAtStartupNamingTheKey() {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .overrideProperty("filters.cors.config.order", "first");
        TestApplication application = new TestApplication.Builder().configuration(configuration).build();

        try (application) {
            RuntimeException failure = assertThrows(RuntimeException.class, application::start);
            assertTrue(messages(failure).contains("filters.cors.config.order must be a whole number"), messages(failure));
        }
    }

    @Test
    void servletInstanceIsMountedAtItsConfiguredPath() {
        Components components = Components.builder().servlet("hello", new HelloServlet("hi there")).build();
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .servlets(Servlets.builder().servlet(Servlet.builder("hello").pathSpec("/hello")));

        try (TestApplication application = start(configuration, components)) {
            assertEquals("hi there", TestClient.create(application).get("/hello").expect200Ok().body());
        }
    }

    @Test
    void servletInstanceWithoutAPathFailsAtStartupNamingTheKey() {
        Components components = Components.builder().servlet("hello", new HelloServlet("hi")).build();
        TestApplication application = new TestApplication.Builder().components(components).build();

        try (application) {
            RuntimeException failure = assertThrows(RuntimeException.class, application::start);
            assertTrue(messages(failure).contains("servlets.hello.config.pathSpec"), messages(failure));
        }
    }

    @Test
    void enabledFilterWithNeitherClassNorInstanceFailsAtStartup() {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .filters(Filters.builder().filter(Filter.builder("ghost")));
        TestApplication application = new TestApplication.Builder().configuration(configuration).build();

        try (application) {
            RuntimeException failure = assertThrows(RuntimeException.class, application::start);
            assertTrue(messages(failure).contains("filters.ghost.config.class"), messages(failure));
        }
    }

    @Test
    void registeringTwoFiltersUnderOneNameIsRejected() {
        Components.Builder components = Components.builder().filter("audit", new Marker("a"));
        Marker second = new Marker("b");

        assertThrows(IllegalArgumentException.class, () -> components.filter("audit", second));
    }

    private static String messages(Throwable failure) {
        StringBuilder text = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            text.append(t).append('\n');
        }
        return text.toString();
    }
}
