package io.descoped.stride.application.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.servlet.http.HttpFilter;
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
            assertFalse(document.path("paths").has("/unregistered"),
                    "a JAX-RS class that is on the class path but not registered is not served, so not described");
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

    @Test
    void declaresTheBearerSchemeWhenAFilterNamedSecurityAuthenticates() throws Exception {
        // stands in for the security module's filter, which is not on this module's class path
        Components components = Components.builder().filter("security", new HttpFilter() {
        }).build();
        try (TestApplication application = new TestApplication.Builder()
                .configuration(configuration()).components(components).build()) {
            application.start();

            String body = TestClient.create(application).get("/openapi.json").expect200Ok().body();
            JsonNode document = new ObjectMapper().readTree(body);

            assertEquals("bearer", document.at("/components/securitySchemes/bearerAuth/scheme").asText(), body);
            assertTrue(document.at("/security/0").has("bearerAuth"), body);
        }
    }

    @Test
    void aSecuritySettingThatNothingEnforcesIsNotAdvertised() throws Exception {
        ApplicationConfiguration.Builder configuration = configuration().overrideProperty("security.enabled", "true");
        try (TestApplication application = new TestApplication.Builder().configuration(configuration).build()) {
            application.start();

            String body = TestClient.create(application).get("/openapi.json").expect200Ok().body();

            assertFalse(new ObjectMapper().readTree(body).has("security"), body);
        }
    }

    @Test
    void describesAResourceHandedInAsAnInstance() throws Exception {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder().testDefaults()
                .resources(Resources.builder()
                        .resource(Resource.builder("openapi").clazz(ApplicationOpenApiResource.class)));
        Components components = Components.builder().resource(new PetResource()).build();

        try (TestApplication application = new TestApplication.Builder()
                .configuration(configuration).components(components).build()) {
            application.start();

            JsonNode document = new ObjectMapper()
                    .readTree(TestClient.create(application).get("/openapi.json").expect200Ok().body());

            assertTrue(document.path("paths").has("/pets/{name}"), document.toString());
            assertFalse(document.path("paths").has("/unregistered"), document.toString());
        }
    }

    @Test
    void anOperationCanSayThatItNeedsNoTokenInAnApplicationThatAuthenticates() throws Exception {
        Components components = Components.builder()
                .filter("security", new HttpFilter() {
                })
                .resource(new StatusResource())
                .build();
        try (TestApplication application = new TestApplication.Builder()
                .configuration(configuration()).components(components).build()) {
            application.start();

            JsonNode document = new ObjectMapper()
                    .readTree(TestClient.create(application).get("/openapi.json").expect200Ok().body());

            assertEquals("[{}]", document.at("/paths/~1status/get/security").toString(), document.toString());
            assertTrue(document.at("/paths/~1pets~1{name}/get/security").isMissingNode(),
                    "other operations follow the document's requirement");
            assertEquals("[{\"bearerAuth\":[]}]", document.path("security").toString());
        }
    }
}
