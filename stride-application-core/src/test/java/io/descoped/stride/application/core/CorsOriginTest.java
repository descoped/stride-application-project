package io.descoped.stride.application.core;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.cors.ApplicationCORSServletFilter;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The CORS filter with a list of allowed origins, where the default allows every origin.
 */
class CorsOriginTest {

    @Path("/stock")
    public static class StockResource {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String stock() {
            return "42";
        }
    }

    private static TestApplication start(String allowedOrigins) {
        TestApplication application = new TestApplication.Builder()
                .configuration(ApplicationConfiguration.builder().testDefaults()
                        .overrideProperty("filters.cors.config.origin", allowedOrigins))
                .components(Components.builder().resource(new StockResource()).build())
                .build();
        application.start();
        return application;
    }

    @Test
    void aRequestFromAnAllowedOriginIsServedAndNamesThatOrigin() {
        try (TestApplication application = start("https://shop.example.test, https://admin.example.test")) {
            HttpResponse<String> response = TestClient.create(application)
                    .get("/stock", "Origin", "https://admin.example.test").expect200Ok().response();

            assertEquals("42", response.body());
            assertEquals(Optional.of("https://admin.example.test"),
                    response.headers().firstValue("Access-Control-Allow-Origin"));
            assertEquals(Optional.of("Origin"), response.headers().firstValue("Vary"));
        }
    }

    @Test
    void aRequestFromAnOriginThatIsNotAllowedIsRefusedBeforeItReachesTheResource() {
        try (TestApplication application = start("https://shop.example.test")) {
            HttpResponse<String> response = TestClient.create(application)
                    .get("/stock", "Origin", "https://elsewhere.example.test").response();

            assertEquals(403, response.statusCode());
            assertEquals("", response.body());
            assertEquals(Optional.empty(), response.headers().firstValue("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void aRequestWithoutAnOriginIsNotACorsRequestAndIsServed() {
        try (TestApplication application = start("https://shop.example.test")) {
            HttpResponse<String> response = TestClient.create(application).get("/stock").expect200Ok().response();

            assertEquals("42", response.body());
            assertEquals(Optional.empty(), response.headers().firstValue("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void aFilterMadeWithTheBuilderAllowsTheMethodsTheDeclaredDefaultAllows() {
        // the instance replaces the configured filter of the same name
        try (TestApplication application = new TestApplication.Builder()
                .configuration(ApplicationConfiguration.builder().testDefaults())
                .components(Components.builder()
                        .filter("cors", ApplicationCORSServletFilter.builder().build())
                        .build())
                .build()) {
            application.start();
            TestClient client = TestClient.create(application);

            for (String method : "GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD".split(", ")) {
                client.options("/ping", "Origin", "https://shop.example", "Access-Control-Request-Method", method)
                        .expectAnyOf(204);
            }
        }
    }
}
