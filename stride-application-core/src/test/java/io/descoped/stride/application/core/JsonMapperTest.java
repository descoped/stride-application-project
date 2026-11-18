package io.descoped.stride.application.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The JSON a resource reads and writes is mapped by the same {@code ObjectMapper} the application can inject:
 * Stride's by default, the application's own when it offers one.
 */
class JsonMapperTest {

    public record Price(String itemName, int amount) {
    }

    @Path("/prices")
    public static class PriceResource {
        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Price echo(Price price) {
            return price;
        }
    }

    private static TestApplication application(Components.Builder components) {
        return new TestApplication.Builder()
                .configuration(ApplicationConfiguration.builder().testDefaults())
                .components(components.resource(new PriceResource()).build())
                .build();
    }

    private static String post(TestApplication application, String json) {
        return TestClient.create(application)
                .send("POST", "/prices", HttpRequest.BodyPublishers.ofString(json), HttpResponse.BodyHandlers.ofString(),
                        "Content-Type", "application/json")
                .expect200Ok().body();
    }

    @Test
    void aResourceReadsJsonWithStridesMapperWhichIgnoresUnknownProperties() {
        try (TestApplication application = application(Components.builder())) {
            application.start();

            String body = post(application, "{\"itemName\":\"tea\",\"amount\":12,\"currency\":\"NOK\"}");

            assertEquals("{\"itemName\":\"tea\",\"amount\":12}", body);
        }
    }

    @Test
    void aResourceReadsAndWritesJsonWithTheMapperTheApplicationOffers() {
        ObjectMapper snakeCase = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        try (TestApplication application = application(Components.builder().service(ObjectMapper.class, snakeCase))) {
            application.start();

            String body = post(application, "{\"item_name\":\"tea\",\"amount\":12}");

            assertEquals("{\"item_name\":\"tea\",\"amount\":12}", body);
        }
    }

    @Test
    void withStridesMapperSwitchedOffAndNoneOfferedJerseyUsesItsOwnWhichRejectsUnknownProperties() {
        try (TestApplication application = new TestApplication.Builder()
                .configuration(ApplicationConfiguration.builder().testDefaults()
                        .overrideProperty("services.jersey.objectMapper.enabled", "false"))
                .components(Components.builder().resource(new PriceResource()).build())
                .build()) {
            application.start();

            assertNull(application.getServiceLocator().getService(ObjectMapper.class));
            TestClient.create(application)
                    .send("POST", "/prices", HttpRequest.BodyPublishers.ofString("{\"itemName\":\"tea\",\"amount\":12,\"currency\":\"NOK\"}"),
                            HttpResponse.BodyHandlers.ofString(), "Content-Type", "application/json")
                    .expect400BadRequest();
        }
    }
}
