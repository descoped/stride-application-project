package io.descoped.stride.application.core;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Args;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A resource class named in the configuration together with its constructor arguments. Stride looks each argument
 * up as a named service and calls the constructor; the class carries no injection annotations.
 */
class ResourceArgumentsTest {

    public record Rates(int percent) {
    }

    @Path("/vat")
    public static class VatResource {
        private final Rates rates;

        public VatResource(Rates rates) {
            this.rates = rates;
        }

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String vat() {
            return rates.percent() + "%";
        }
    }

    private static ApplicationConfiguration.Builder configuration(Class<?> argumentType, String named) {
        return ApplicationConfiguration.builder().testDefaults()
                .resources(Resources.builder().resource(Resource.builder("vat")
                        .clazz(VatResource.class)
                        .args(Args.builder().arg(argumentType, named))));
    }

    /** A resource whose constructor refuses its argument. */
    @Path("/strict")
    public static class StrictResource {
        public StrictResource(Rates rates) {
            throw new IllegalArgumentException("a rate of " + rates.percent() + " is not allowed");
        }
    }

    private static final Components NORWAY = Components.builder()
            .service(Rates.class, "norway", new Rates(25))
            .build();

    /** Every message in the chain of the failure that stops the start. */
    private static String startFailure(ApplicationConfiguration.Builder configuration, Components components) {
        try (StrideApplication application = StrideApplication.create(configuration.build(), components)) {
            StringBuilder messages = new StringBuilder();
            for (Throwable t = assertThrows(RuntimeException.class, application::start); t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            return messages.toString();
        }
    }

    @Test
    void theResourceIsCreatedWithTheNamedServiceAsItsConstructorArgument() {
        try (TestApplication application = new TestApplication.Builder()
                .configuration(configuration(Rates.class, "norway")).components(NORWAY).build()) {
            application.start();

            assertEquals("25%", TestClient.create(application).get("/vat").expect200Ok().body());
        }
    }

    @Test
    void anArgumentNoServiceProvidesStopsTheApplicationFromStartingAndNamesIt() {
        String failure = startFailure(configuration(Rates.class, "sweden"), NORWAY);

        assertTrue(failure.contains("Resource " + VatResource.class.getName() + " needs a service of type "
                + Rates.class.getName() + " named 'sweden', and there is none"), failure);
    }

    @Test
    void argumentsThatMatchNoConstructorStopTheApplicationFromStartingAndNameTheTypes() {
        Components components = Components.builder().service(String.class, "norway", "25").build();

        String failure = startFailure(configuration(String.class, "norway"), components);

        assertTrue(failure.contains("Resource " + VatResource.class.getName()
                + " could not be created with a constructor taking (java.lang.String)"), failure);
    }

    @Test
    void aConstructorThatFailsStopsTheApplicationFromStartingAndItsOwnFailureIsNamed() {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder().testDefaults()
                .resources(Resources.builder().resource(Resource.builder("strict")
                        .clazz(StrictResource.class)
                        .args(Args.builder().arg(Rates.class, "norway"))));

        String failure = startFailure(configuration, NORWAY);

        assertTrue(failure.contains("The constructor of resource " + StrictResource.class.getName()
                + " failed: java.lang.IllegalArgumentException: a rate of 25 is not allowed"), failure);
    }
}
