package io.descoped.stride.application.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
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
import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    }

    @Test
    void testNewFiltersBuilder() throws JsonProcessingException {
        Filters filters = Filters.builder()
                .filter(Filter.builder("dummy")
                        .enabled(true)
                        .clazz(jakarta.servlet.Filter.class))
                .build();
        log.trace("\n{}", yamlMapper.writeValueAsString(filters.json()));
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

        //log.debug("{}", services.json().toPrettyString());
        log.debug("\n{}", yamlMapper.writeValueAsString(services.json()));

        Optional<Service> service = services.service("jetty.server");
        assertEquals("jetty.server", service
                .map(Service::name)
                .orElse(null));
        Optional<Service> serviceByClass = services.serviceByClass("io.descoped.stride.application.server.JettyServerService");
        assertEquals("jetty.server", serviceByClass
                .map(Service::name)
                .orElse(null));
//        assertEquals("bar", services.serviceName("dummy.service")
//                .map(Services.Service::json)
//                .map(Services.Service::new)
//                .map(e -> e.metadata().value("foo"))
//                .orElse(null));
    }

    @Test
    void testFiltersBuilder() {
        Filters filters = Filters.builder()
                .filter(Filter.builder("dummy")
                        .clazz(jakarta.servlet.Filter.class)
                        .pathSpec("/dummy")
                        .dispatches(EnumSet.of(DispatcherType.FORWARD, DispatcherType.REQUEST)))
                .build();

        //log.debug("filter: {}", filters.json().toPrettyString());
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

        //log.debug("{}", servlets.json().toPrettyString());
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

        //log.debug("{}", servlets.json().toPrettyString());
        assertEquals(GreetingResource.class.getName(), resources.resourceByClass(GreetingResource.class.getName())
                .map(Resource::clazz)
                .map(Class::getName)
                .orElse(null));
    }

    @Test
    void configurationBuilder() throws JsonProcessingException {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .configuration(ApplicationProperties.builder())
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
        log.debug("\n{}", configuration.json().toPrettyString());
        JavaPropsMapper propsMapper = new JavaPropsMapper();
        log.trace("\n{}", propsMapper.writeValueAsString(configuration.json()));
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        log.trace("\n{}", propsMapper.writeValueAsString(configuration.json()));
        log.trace("\n{}", mapper.writeValueAsString(configuration.json()));

        ApplicationProperties.Builder applicationPropertiesBuilder = ApplicationProperties.builder()
                .classpathPropertiesFile("application-defaults.properties")
                .testDefaults();

        ApplicationConfiguration applicationConfiguration = ApplicationConfiguration.builder().configuration(applicationPropertiesBuilder).build();
        log.trace("\n{}", mapper.writeValueAsString(applicationConfiguration.json()));
    }

    @Disabled
    @Test
    void propertiesToDeployment() throws IOException {
        // TODO use a service namespace instead of
        final String props = """
                services.0.serviceName=jetty.server
                services.0.serviceClass=io.descoped.stride.application.server.JettyServerService
                services.1.serviceClass=io.descoped.stride.application.server.JerseyServerService
                services.2.serviceName=dummy.service
                services.2.serviceClass=org.jvnet.hk2.annotations.Service
                services.2.metadata.foo=bar
                filters.0.name=dummy
                filters.0.filterClass=jakarta.servlet.Filter
                filters.0.pathSpec=/dummy
                filters.0.dispatches.0=FORWARD
                filters.0.dispatches.1=REQUEST
                servlets.0.servletClass=io.dropwizard.metrics.servlets.AdminServlet
                servlets.0.pathSpec=/admin
                servlets.1.servletName=metrics
                servlets.1.servletClass=io.dropwizard.metrics.servlets.MetricsServlet
                servlets.1.pathSpec=/metrics
                """;


//        ApplicationProperties applicationProperties = ApplicationProperties.builder().map(ApplicationJson.propertiesToMap(props)).build();

//        ApplicationConfiguration configuration = ApplicationConfiguration.builder().configuration(applicationProperties).build(); // ((ObjectNode) applicationJson.json());

//        log.debug("\n{}", configuration.json().toPrettyString());
    }
}
