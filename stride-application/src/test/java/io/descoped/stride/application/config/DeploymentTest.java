package io.descoped.stride.application.config;

import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeploymentTest {

    private static final Logger log = LoggerFactory.getLogger(DeploymentTest.class);

    @Test
    void testServicesBuilder() {

    }

    @Test
    void testFiltersBuilder() {

    }

    @Test
    void testServletsBuilder() {
        Servlets servlets = new Servlets.Builder()
                .servlet(null, AdminServlet.class, "/admin")
                .servlet("metrics", MetricsServlet.class, "/metrics")
                .build();

        log.debug("{}", servlets.json().toPrettyString());
        assertNull(servlets.servletName("admin").with("servletName").asString().orElse(null), "Should be null");
        assertEquals("io.dropwizard.metrics.servlets.AdminServlet", servlets.servletClass("io.dropwizard.metrics.servlets.AdminServlet").with("servletClass").asString().orElseThrow());
        assertEquals("metrics", servlets.servletName("metrics").with("servletName").asString().orElseThrow());
        assertEquals("io.dropwizard.metrics.servlets.MetricsServlet", servlets.servletName("metrics").with("servletClass").asString().orElseThrow());
    }

    @Test
    void testServletsMappingBuilder() {

    }
}
