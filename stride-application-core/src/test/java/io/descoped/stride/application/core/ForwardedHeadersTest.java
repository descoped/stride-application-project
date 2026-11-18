package io.descoped.stride.application.core;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code services.jetty.server.config.forwarded.enabled}: whether the address a proxy forwards is the address the
 * application sees. Off by default, because the headers are only trustworthy behind a proxy that sets them.
 */
class ForwardedHeadersTest {

    @Path("/where")
    public static class WhereResource {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String where(@Context UriInfo uriInfo) {
            return uriInfo.getBaseUri().toString();
        }
    }

    private static TestApplication start(boolean forwarded) {
        TestApplication application = new TestApplication.Builder()
                .configuration(ApplicationConfiguration.builder().testDefaults()
                        .overrideProperty("services.jetty.server.config.forwarded.enabled", Boolean.toString(forwarded)))
                .components(Components.builder().resource(new WhereResource()).build())
                .build();
        application.start();
        return application;
    }

    private static String whereAsSeenThroughAProxy(TestApplication application) {
        return TestClient.create(application).get("/where",
                "X-Forwarded-Proto", "https",
                "X-Forwarded-Host", "api.example.test").expect200Ok().body();
    }

    @Test
    void whenEnabledTheApplicationSeesTheAddressTheProxyForwards() {
        try (TestApplication application = start(true)) {
            assertEquals("https://api.example.test/", whereAsSeenThroughAProxy(application));
        }
    }

    @Test
    void byDefaultTheForwardedHeadersAreIgnored() {
        try (TestApplication application = start(false)) {
            assertEquals("http://localhost:" + application.getLocalPort() + "/",
                    whereAsSeenThroughAProxy(application));
        }
    }
}
