package io.descoped.stride.application.core;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.config.Servlet;
import io.descoped.stride.application.config.ServletContextBinding;
import io.descoped.stride.application.config.ServletContextInitialization;
import io.descoped.stride.application.config.ServletContextValidation;
import io.descoped.stride.application.config.Servlets;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a servlet declared in the configuration can ask of the servlet context: a service bound to an attribute, and
 * attributes and named services that must be there, or the application does not start.
 */
class ServletContextWiringTest {

    /** A service created by Stride from the configuration. */
    public static class Stock {
        public int of(String item) {
            return "tea".equals(item) ? 12 : 0;
        }
    }

    /** Reads its collaborator from the servlet context, where the configuration bound it. */
    public static class StockServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            Stock stock = (Stock) getServletContext().getAttribute("stock");
            response.setContentType("text/plain");
            response.getWriter().print(stock.of(request.getParameter("item")));
        }
    }

    /** Sets an attribute and offers a named service, as an initializer for a metrics library would. */
    public static class Announcer implements ServletContextInitializer {
        @Override
        public void initialize(ServiceLocator locator, ServletContext context) {
            context.setAttribute("announced", "yes");
            ServiceLocatorUtilities.addOneConstant(locator, "ready", "announcement", String.class);
        }
    }

    /** Runs before {@link Announcer} and provides none of what the initialization requires. */
    public static class Bystander implements ServletContextInitializer {
        @Override
        public void initialize(ServiceLocator locator, ServletContext context) {
            context.setAttribute("bystander", "ran");
        }
    }

    public static class AnnouncedServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.getWriter().print(getServletContext().getAttribute("announced"));
        }
    }

    private static Servlets.Builder stockServlet(Servlet.Builder servlet) {
        return Servlets.builder().servlet(servlet.clazz(StockServlet.class).pathSpec("/stock"));
    }

    private static String messagesOf(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }

    private static String startFailure(ApplicationConfiguration.Builder configuration) {
        try (StrideApplication application = StrideApplication.create(configuration.build())) {
            return messagesOf(assertThrows(RuntimeException.class, application::start));
        }
    }

    @Test
    void aServiceBoundToAServletContextAttributeIsThereForTheServlet() {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder().testDefaults()
                .services(Services.builder().service(Service.builder("stockService").clazz(Stock.class).runLevel(5)))
                .servlets(stockServlet(Servlet.builder("stock")
                        .binding(ServletContextBinding.builder().bind("stock", "stockService"))
                        .validate(ServletContextValidation.builder().requireAttribute("stock"))));

        try (TestApplication application = new TestApplication.Builder().configuration(configuration).build()) {
            application.start();

            assertEquals("12", TestClient.create(application).get("/stock?item=tea").expect200Ok().body());
        }
    }

    @Test
    void aServletThatRequiresAnAttributeNobodySetStopsTheApplicationFromStarting() {
        String failure = startFailure(ApplicationConfiguration.builder().testDefaults()
                .servlets(stockServlet(Servlet.builder("stock")
                        .validate(ServletContextValidation.builder().requireAttribute("stock")))));

        assertTrue(failure.contains("Required servletContext attribute 'stock' for 'stock' servlet IS NOT set!"),
                failure);
    }

    @Test
    void anInitializerSetsWhatAServletReadsAndItsOwnRequirementsAreMet() {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder().testDefaults()
                .initialization(ServletContextInitialization.builder()
                        .initializerClass(Announcer.class)
                        .validate(ServletContextValidation.builder()
                                .requireAttribute("announced")
                                .requireNamedService(String.class, "announcement")))
                .servlets(Servlets.builder().servlet(Servlet.builder("announced")
                        .clazz(AnnouncedServlet.class).pathSpec("/announced")));

        try (TestApplication application = new TestApplication.Builder().configuration(configuration).build()) {
            application.start();

            assertEquals("yes", TestClient.create(application).get("/announced").expect200Ok().body());
        }
    }

    @Test
    void theRequirementsAreCheckedWhenEveryInitializerHasRunSoALaterOneCanMeetThem() {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder().testDefaults()
                .initialization(ServletContextInitialization.builder()
                        .initializerClass(Bystander.class)
                        .initializerClass(Announcer.class)
                        .validate(ServletContextValidation.builder()
                                .requireAttribute("bystander")
                                .requireAttribute("announced")
                                .requireNamedService(String.class, "announcement")))
                .servlets(Servlets.builder().servlet(Servlet.builder("announced")
                        .clazz(AnnouncedServlet.class).pathSpec("/announced")));

        try (TestApplication application = new TestApplication.Builder().configuration(configuration).build()) {
            application.start();

            assertEquals("yes", TestClient.create(application).get("/announced").expect200Ok().body());
        }
    }

    @Test
    void anInitializationThatRequiresAnAttributeNobodySetStopsTheApplicationFromStarting() {
        String failure = startFailure(ApplicationConfiguration.builder().testDefaults()
                .initialization(ServletContextInitialization.builder()
                        .initializerClass(Announcer.class)
                        .validate(ServletContextValidation.builder().requireAttribute("missing"))));

        assertTrue(failure.contains(
                "Required servletContext attribute 'missing' for the servlet context initialization IS NOT set!"), failure);
    }

    @Test
    void anInitializationThatRequiresANamedServiceNobodyOffersStopsTheApplicationFromStarting() {
        String failure = startFailure(ApplicationConfiguration.builder().testDefaults()
                .initialization(ServletContextInitialization.builder()
                        .initializerClass(Announcer.class)
                        .validate(ServletContextValidation.builder().requireNamedService(String.class, "absent"))));

        assertTrue(failure.contains("Required service 'java.lang.String' named 'absent' for the servlet context initialization is NOT available!"),
                failure);
    }

    @Test
    void aServletThatRequiresANamedServiceNobodyOffersStopsTheApplicationFromStarting() {
        String failure = startFailure(ApplicationConfiguration.builder().testDefaults()
                .servlets(Servlets.builder().servlet(Servlet.builder("announced")
                        .clazz(AnnouncedServlet.class).pathSpec("/announced")
                        .validate(ServletContextValidation.builder().requireNamedService(String.class, "announcement")))));

        assertTrue(failure.contains("Required service 'java.lang.String' named 'announcement' for 'announced' servlet"
                + " is NOT available!"), failure);
    }

    @Test
    void aServletClassWithoutAPathStopsTheApplicationFromStartingAndNamesTheKey() {
        String failure = startFailure(ApplicationConfiguration.builder().testDefaults()
                .servlets(Servlets.builder().servlet(Servlet.builder("announced").clazz(AnnouncedServlet.class))));

        assertTrue(failure.contains("Servlet 'announced' has no path: set servlets.announced.config.pathSpec"), failure);
    }

    @Test
    void servletsAndInitializersAreThereWhenJerseyIsDisabled() {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder().testDefaults()
                .overrideProperty("services.jersey.server.enabled", "false")
                .initialization(ServletContextInitialization.builder().initializerClass(Announcer.class))
                .servlets(Servlets.builder().servlet(Servlet.builder("announced")
                        .clazz(AnnouncedServlet.class).pathSpec("/announced")
                        .validate(ServletContextValidation.builder().requireNamedService(String.class, "announcement"))));

        try (TestApplication application = new TestApplication.Builder().configuration(configuration).build()) {
            application.start();

            TestClient client = TestClient.create(application);
            assertEquals("yes", client.get("/announced").expect200Ok().body());
            client.get("/ping").expect200Ok();
        }
    }
}
