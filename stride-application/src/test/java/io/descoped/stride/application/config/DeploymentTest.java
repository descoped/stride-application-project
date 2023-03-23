package io.descoped.stride.application.config;

import io.descoped.stride.application.server.JerseyServerService;
import io.descoped.stride.application.server.JettyServerService;
import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeploymentTest {

    private static final Logger log = LoggerFactory.getLogger(DeploymentTest.class);

    @Test
    void testServicesBuilder() {
        Services services = Services.builder()
                .service(Services.serviceBuilder()
                        .name("jetty.server")
                        .clazz(JettyServerService.class))
                .service(Services.serviceBuilder()
                        .clazz(JerseyServerService.class))
                .service(Services.serviceBuilder()
                        .name("dummy.service")
                        .clazz(Service.class)
                        .metadata(Services.metadataBuilder()
                                .property("foo", "bar")))
                .build();

        //log.debug("{}", services.json().toPrettyString());
        assertEquals("jetty.server", services.serviceName("jetty.server")
                .map(Services.Service::name)
                .orElse(null));
        assertEquals("jetty.server", services.serviceClass("io.descoped.stride.application.server.JettyServerService")
                .map(Services.Service::name)
                .orElse(null));
        assertNull(services.serviceName("jersey.server")
                        .map(Services.Service::name)
                        .orElse(null),
                "Should be null");
        assertEquals("bar", services.serviceName("dummy.service")
                .map(Services.Service::json)
                .map(Services.Service::new)
                .map(e -> e.metadata().value("foo"))
                .orElse(null));
    }

    @Test
    void testFiltersBuilder() {
        Filters filters = Filters.builder()
                .filter(Filters.filterBuilder()
                        .name("dummy")
                        .clazz(jakarta.servlet.Filter.class)
                        .pathSpec("/dummy")
                        .dispatches(EnumSet.of(DispatcherType.FORWARD, DispatcherType.REQUEST)))
                .build();

        //log.debug("filter: {}", filters.json().toPrettyString());
        assertEquals("dummy", filters.filterName("dummy")
                .map(Filters.Filter::name)
                .orElse(null));
        assertEquals("jakarta.servlet.Filter", filters.filterClass("jakarta.servlet.Filter")
                .map(Filters.Filter::className)
                .orElse(null));
        assertEquals("/dummy", filters.filterName("dummy")
                .map(Filters.Filter::pathSpec)
                .orElse(null));
        assertEquals(EnumSet.of(DispatcherType.FORWARD, DispatcherType.REQUEST), filters.filterName("dummy")
                .map(Filters.Filter::dispatches)
                .orElse(null));
    }

    @Test
    void testServletsBuilder() {
        Servlets servlets = Servlets.builder()
                .servlet(Servlets.servletBuilder()
                        .clazz(AdminServlet.class)
                        .pathSpec("/admin"))
                .servlet(Servlets.servletBuilder()
                        .name("metrics")
                        .clazz(MetricsServlet.class)
                        .pathSpec("/metrics"))
                .build();

        //log.debug("{}", servlets.json().toPrettyString());
        assertEquals("io.dropwizard.metrics.servlets.AdminServlet", servlets.servletClass("io.dropwizard.metrics.servlets.AdminServlet")
                .map(Servlets.Servlet::clazz)
                .map(Class::getName)
                .orElse(null));

        assertEquals("metrics", servlets.servletName("metrics")
                .map(Servlets.Servlet::name)
                .orElse(null));

        assertEquals("io.dropwizard.metrics.servlets.MetricsServlet", servlets.servletName("metrics")
                .map(Servlets.Servlet::className)
                .orElse(null));
    }
}
