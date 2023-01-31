package io.descoped.application;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.descoped.application.factory.InstanceFactory;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EmbeddedApplicationTest {

    static {
        Logging.init();
    }

//  @Inject
//  TestServer server;
//
//  @Inject
//  TestClient client;

    static final Logger log = LoggerFactory.getLogger(EmbeddedApplicationTest.class);
    static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testBoostrapApp() throws InterruptedException, IOException {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("server.port", Integer.toString(10990));
        config.put("server.context-path", "/");
        config.put("application.url", "https://notarealcompany.bad/app");

        ApplicationProperties applicationProperties = ApplicationProperties.builder()
                .testDefaults()
                .build();
        Configuration configuration = Configuration.create(applicationProperties);

        Application application = new Application(configuration, new InstanceFactory());
        application.initBuiltinDefaults();

        application.initAndRegisterJaxRsWsComponent(GreetingResource.class,
                EmbeddedApplicationTest::createGreetingResource);

        application.start();

        HttpClient client = HttpClient.newBuilder().build();

        {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(String.format("http://localhost:%s/greet/arunkumar?greeting=hello",
                                    application.getPort())))
                            .build(),
                    BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            log.info("{}\n{}", response.statusCode(), mapper.readTree(response.body()).toPrettyString());
        }

        application.stop();
    }

    static GreetingResource createGreetingResource() {
        return new GreetingResource();
    }

    @Path("/greet")
    public static class GreetingResource {

        public GreetingResource() {
        }

        @GET
        @Path("/{name}")
        @Produces(MediaType.APPLICATION_JSON)
        @Timed
        public Greeting greet(@PathParam("name") String name,
                              @QueryParam("greeting") String greetingParam) {
            if (greetingParam != null) {
                return new Greeting(name, greetingParam);
            }
            throw new UnsupportedOperationException();
        }
    }

    static class Greeting {

        final String name;
        final String greeting;

        @JsonCreator
        public Greeting(@JsonProperty("name") String name, @JsonProperty("greeting") String greeting) {
            this.name = name;
            this.greeting = greeting;
        }

        public String getName() {
            return name;
        }

        public String getGreeting() {
            return greeting;
        }

        @Override
        public String toString() {
            return "Greeting{" +
                    "name='" + name + '\'' +
                    ", greeting='" + greeting + '\'' +
                    '}';
        }
    }

}
