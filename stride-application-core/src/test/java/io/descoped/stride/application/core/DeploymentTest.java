package io.descoped.stride.application.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.config.Metadata;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.config.Servlet;
import io.descoped.stride.application.config.Servlets;
import io.descoped.stride.application.server.JerseyServerService;
import io.descoped.stride.application.server.JettyServerService;
import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentTest {

    private static final Logger log = LoggerFactory.getLogger(DeploymentTest.class);
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void emptyFilters() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .build();

        Services services = configuration.services();
        assertNotNull(services);

        Filters filters = configuration.filters();
        assertNotNull(filters);

        Servlets servlets = configuration.servlets();
        assertNotNull(servlets);

        Resources resources = configuration.resources();
        assertNotNull(resources);
    }

    @Test
    void testNewServicesBuilder() throws JsonProcessingException {
        Services services = Services.builder()
                .service(Service.builder("jetty.server")
                        .enabled(true)
                        .clazz(JettyServerService.class)
                        .runLevel(10)
                        .metadata(Metadata.builder().property("foo", "bar")))
                .build();
        log.trace("\n{}", yamlMapper.writeValueAsString(services.json()));

        Service service = services.service("jetty.server").orElseThrow();
        assertTrue(service.isEnabled());
        assertEquals(JettyServerService.class, service.clazz());
        assertEquals(10, service.runLevel());
        assertEquals("bar", service.metadata().value("foo"));
    }

    @Test
    void testNewFiltersBuilder() throws JsonProcessingException {
        Filters filters = Filters.builder()
                .filter(Filter.builder("dummy")
                        .enabled(true)
                        .clazz(jakarta.servlet.Filter.class))
                .build();
        log.trace("\n{}", yamlMapper.writeValueAsString(filters.json()));

        Filter filter = filters.filter("dummy").orElseThrow();
        assertTrue(filter.isEnabled());
        assertEquals(jakarta.servlet.Filter.class, filter.clazz());
    }

    @Test
    void testServicesBuilder() throws JsonProcessingException {
        Services services = Services.builder()
                .service(Service.builder("jetty.server")
                        .enabled(true)
                        .clazz(JettyServerService.class))
                .service(Service.builder("jersey.server")
                        .clazz(JerseyServerService.class))
                .service(Service.builder("dummy.service")
                        .clazz(Service.class)
                        .metadata(Metadata.builder()
                                .property("foo", "bar")))
                .build();

        log.debug("\n{}", yamlMapper.writeValueAsString(services.json()));

        Optional<Service> service = services.service("jetty.server");
        assertEquals("jetty.server", service
                .map(Service::name)
                .orElse(null));
        Optional<Service> serviceByClass = services.serviceByClass("io.descoped.stride.application.server.JettyServerService");
        assertEquals("jetty.server", serviceByClass
                .map(Service::name)
                .orElse(null));
    }

    @Test
    void testFiltersBuilder() {
        Filters filters = Filters.builder()
                .filter(Filter.builder("dummy")
                        .clazz(jakarta.servlet.Filter.class)
                        .pathSpec("/dummy")
                        .dispatches(EnumSet.of(DispatcherType.FORWARD, DispatcherType.REQUEST)))
                .build();

        assertEquals("dummy", filters.filter("dummy")
                .map(Filter::name)
                .orElse(null));
        assertEquals("jakarta.servlet.Filter", filters.filterByClass("jakarta.servlet.Filter")
                .map(Filter::className)
                .orElse(null));
        assertEquals("/dummy", filters.filter("dummy")
                .map(Filter::pathSpec)
                .orElse(null));
        assertEquals(EnumSet.of(DispatcherType.FORWARD, DispatcherType.REQUEST), filters.filter("dummy")
                .map(Filter::dispatches)
                .orElse(null));
    }

    @Test
    void testServletsBuilder() {
        Servlets servlets = Servlets.builder()
                .servlet(Servlet.builder("admin")
                        .clazz(AdminServlet.class)
                        .pathSpec("/admin"))
                .servlet(Servlet.builder("metrics")
                        .clazz(MetricsServlet.class)
                        .pathSpec("/metrics"))
                .build();

        assertEquals("io.dropwizard.metrics.servlets.AdminServlet", servlets.servletByClass("io.dropwizard.metrics.servlets.AdminServlet")
                .map(Servlet::clazz)
                .map(Class::getName)
                .orElse(null));

        assertEquals("metrics", servlets.servlet("metrics")
                .map(Servlet::name)
                .orElse(null));

        assertEquals("io.dropwizard.metrics.servlets.MetricsServlet", servlets.servlet("metrics")
                .map(Servlet::className)
                .orElse(null));
    }

    @Test
    void testResourcesBuilder() {
        Resources resources = Resources.builder()
                .resource(Resource.builder("greeting")
                        .clazz(GreetingResource.class))
                .build();

        assertEquals(GreetingResource.class.getName(), resources.resourceByClass(GreetingResource.class.getName())
                .map(Resource::clazz)
                .map(Class::getName)
                .orElse(null));
    }

    @Test
    void configurationBuilder() throws JsonProcessingException {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .services(Services.builder()
                        .service(Service.builder("jetty.server")
                                .clazz(JettyServerService.class))
                        .service(Service.builder("jersey.server")
                                .clazz(JerseyServerService.class))
                        .service(Service.builder("dummy.service")
                                .clazz(Service.class)
                                .metadata(Metadata.builder()
                                        .property("foo", "bar"))))
                .filters(Filters.builder()
                        .filter(Filter.builder("dummy")
                                .clazz(jakarta.servlet.Filter.class)
                                .pathSpec("/dummy")
                                .dispatches(EnumSet.of(DispatcherType.FORWARD, DispatcherType.REQUEST))))
                .servlets(Servlets.builder()
                        .servlet(Servlet.builder("admin")
                                .clazz(AdminServlet.class)
                                .pathSpec("/admin"))
                        .servlet(Servlet.builder("metrics")
                                .clazz(MetricsServlet.class)
                                .pathSpec("/metrics")))
                .resources(Resources.builder()
                        .resource(Resource.builder("greeting")
                                .clazz(GreetingResource.class)))
                .build();
        log.trace("\n{}", yamlMapper.writeValueAsString(configuration.json()));

        // what was declared in code is what the configuration holds
        Services services = configuration.services();
        assertEquals(JettyServerService.class, services.service("jetty.server").orElseThrow().clazz());
        assertEquals(JerseyServerService.class, services.service("jersey.server").orElseThrow().clazz());
        assertEquals("bar", services.service("dummy.service").orElseThrow().metadata().value("foo"));

        Filter filter = configuration.filters().filter("dummy").orElseThrow();
        assertEquals(jakarta.servlet.Filter.class, filter.clazz());
        assertEquals("/dummy", filter.pathSpec());

        Servlets servlets = configuration.servlets();
        assertEquals(AdminServlet.class, servlets.servlet("admin").orElseThrow().clazz());
        assertEquals("/admin", servlets.servlet("admin").orElseThrow().pathSpec());
        assertEquals(MetricsServlet.class, servlets.servlet("metrics").orElseThrow().clazz());
        assertEquals("/metrics", servlets.servlet("metrics").orElseThrow().pathSpec());

        assertEquals(GreetingResource.class,
                configuration.resources().resource("greeting").orElseThrow().clazz());
    }
}
