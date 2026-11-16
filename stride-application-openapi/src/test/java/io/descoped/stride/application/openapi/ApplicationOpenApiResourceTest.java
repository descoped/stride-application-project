package io.descoped.stride.application.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationOpenApiResourceTest {

    private static ApplicationConfiguration.Builder configuration() {
        return ApplicationConfiguration.builder()
                .testDefaults()
                .overrideProperty("application.alias", "petstore")
                .overrideProperty("application.version", "1.2.3")
                .resources(Resources.builder()
                        .resource(Resource.builder("pets").enabled(true).clazz(PetResource.class))
                        .resource(Resource.builder("openapi").enabled(true).clazz(ApplicationOpenApiResource.class)));
    }

    @Test
    void servesTheDocumentOfTheRegisteredResourcesAsJson() throws Exception {
        try (TestApplication application = new TestApplication.Builder().configuration(configuration()).build()) {
            application.start();

            String body = TestClient.create(application).get("/openapi.json").expect200Ok().body();
            JsonNode document = new ObjectMapper().readTree(body);

            assertTrue(document.path("openapi").asText().startsWith("3."), body);
            assertEquals("petstore API", document.at("/info/title").asText());
            assertEquals("1.2.3", document.at("/info/version").asText());
            assertTrue(document.path("paths").has("/pets/{name}"), body);
            assertTrue(document.at("/paths/~1pets~1{name}").has("get"), body);
            assertFalse(document.path("paths").has("/openapi"), "the document does not list itself");
            assertFalse(document.has("servers"), "no server entry unless application.url is set");
            assertFalse(document.has("security"), "no security requirement unless security is enabled");
        }
    }

    @Test
    void servesYamlAndTheConfiguredServerUrl() {
        ApplicationConfiguration.Builder configuration = configuration()
                .overrideProperty("application.url", "https://api.example.test/v1");
        try (TestApplication application = new TestApplication.Builder().configuration(configuration).build()) {
            application.start();

            String body = TestClient.create(application).get("/openapi.yaml").expect200Ok().body();

            assertTrue(body.contains("title: petstore API"), body);
            assertTrue(body.contains("url: https://api.example.test/v1"), body);
            assertTrue(body.contains("/pets/{name}:"), body);
        }
    }

    // Not over HTTP: an application with security enabled reads the issuer's metadata and keys over HTTPS before it
    // starts, and there is no issuer here.
    @Test
    void declaresTheBearerSchemeWhenSecurityIsEnabled() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .overrideProperty("security.enabled", "true")
                .overrideProperty("security.issuer", "https://issuer.example.test")
                .overrideProperty("security.audiences", "https://api.example.test")
                .build();

        var document = ApplicationOpenApiResource.describe(configuration);

        assertEquals("bearer", document.getComponents().getSecuritySchemes()
                .get(ApplicationOpenApiResource.BEARER_SCHEME).getScheme());
        assertTrue(document.getSecurity().get(0).containsKey(ApplicationOpenApiResource.BEARER_SCHEME));
    }
}
