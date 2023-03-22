package io.descoped.stride.application.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.descoped.stride.application.server.JerseyServerService;
import io.descoped.stride.application.server.JettyServerService;
import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DeploymentTest {

    private static final Logger log = LoggerFactory.getLogger(DeploymentTest.class);

    @Test
    void testServicesBuilder() {
        Services services = Services.builder()
            .service("jetty.server", JettyServerService.class)
            .service(null, JerseyServerService.class)
            .service("dummy.service", Service.class,
                Services.metadataBuilder().property("foo", "bar"))
            .build();

        //log.debug("{}", services.json().toPrettyString());
        assertEquals("jetty.server",
            services.serviceName("jetty.server").with("serviceName").asString().orElseThrow());
        assertEquals("jetty.server",
            services.serviceClass("io.descoped.stride.application.server.JettyServerService")
                .with("serviceName").asString().orElseThrow());
        assertNull(
            services.serviceName("jersey.server").with("servletName").asString().orElse(null),
            "Should be null");
        assertEquals("bar", services.serviceName("dummy.service").asString("metadata.foo", null));
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

        //log.debug("{}", servlets.json().toPrettyString());
        assertNull(servlets.servletName("admin").with("servletName").asString().orElse(null), "Should be null");
        assertEquals("io.dropwizard.metrics.servlets.AdminServlet", servlets.servletClass("io.dropwizard.metrics.servlets.AdminServlet").with("servletClass").asString().orElseThrow());
        assertEquals("metrics", servlets.servletName("metrics").with("servletName").asString().orElseThrow());
        assertEquals("io.dropwizard.metrics.servlets.MetricsServlet", servlets.servletName("metrics").with("servletClass").asString().orElseThrow());
    }

    @Test
    void testServletsMappingBuilder() {

    }
}
