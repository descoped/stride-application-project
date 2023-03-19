package io.descoped.stride.application;

import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class StrideApplicationTest {

    private static final Logger log = LoggerFactory.getLogger(StrideApplicationTest.class);

    @Test
    void testBootstrap() throws IOException, InterruptedException {
        ApplicationProperties properties = ApplicationProperties.builder()
                .classpathPropertiesFile("application-defaults.properties")
                .testDefaults()
                .build();

        try (StrideApplication application = new StrideApplication(properties)) {
            application.start();
            log.trace("port: {}", application.gePort());

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + application.gePort() + "/ping")).GET().build(), HttpResponse.BodyHandlers.ofString());
            log.trace("resp: {}", response.statusCode());
        }
    }

}
