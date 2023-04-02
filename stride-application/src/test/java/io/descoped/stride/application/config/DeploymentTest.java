package io.descoped.stride.application.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.descoped.stride.application.EmbeddedApplicationTest;
import io.descoped.stride.application.server.JerseyServerService;
import io.descoped.stride.application.server.JettyServerService;
import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import jakarta.servlet.DispatcherType;
import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeploymentTest {

    private static final Logger log = LoggerFactory.getLogger(DeploymentTest.class);

    @Test
    void emptyFilters() {
        Deployment deployment = Deployment.builder()
                .build();

        Services services = deployment.services();
        assertNotNull(services);

        Filters filters = deployment.filters();
        assertNotNull(filters);

        Servlets servlets = deployment.servlets();
        assertNotNull(servlets);

        Resources resources = deployment.resources();
        assertNotNull(resources);
    }

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

    @Test
    void testResourcesBuilder() {
        Resources resources = Resources.builder()
                .resource(Resources.resourceBuilder()
                        .clazz(EmbeddedApplicationTest.GreetingResource.class))
                .build();

        //log.debug("{}", servlets.json().toPrettyString());
        assertEquals(EmbeddedApplicationTest.GreetingResource.class.getName(), resources.clazz(EmbeddedApplicationTest.GreetingResource.class.getName())
                .map(Resources.Resource::clazz)
                .map(Class::getName)
                .orElse(null));
    }

    @Test
    void deploymentBuilder() throws JsonProcessingException {
        Deployment deployment = Deployment.builder()
                .services(Services.builder()
                        .service(Services.serviceBuilder()
                                .name("jetty.server")
                                .clazz(JettyServerService.class))
                        .service(Services.serviceBuilder()
                                .clazz(JerseyServerService.class))
                        .service(Services.serviceBuilder()
                                .name("dummy.service")
                                .clazz(Service.class)
                                .metadata(Services.metadataBuilder()
                                        .property("foo", "bar"))))
                .filters(Filters.builder()
                        .filter(Filters.filterBuilder()
                                .name("dummy")
                                .clazz(jakarta.servlet.Filter.class)
                                .pathSpec("/dummy")
                                .dispatches(EnumSet.of(DispatcherType.FORWARD, DispatcherType.REQUEST))))
                .servlets(Servlets.builder()
                        .servlet(Servlets.servletBuilder()
                                .clazz(AdminServlet.class)
                                .pathSpec("/admin"))
                        .servlet(Servlets.servletBuilder()
                                .name("metrics")
                                .clazz(MetricsServlet.class)
                                .pathSpec("/metrics")))
                .resources(Resources.builder()
                        .resource(Resources.resourceBuilder()
                                .clazz(EmbeddedApplicationTest.GreetingResource.class)))
                .build();
        log.debug("\n{}", deployment.json().toPrettyString());
        JavaPropsMapper propsMapper = new JavaPropsMapper();
        log.trace("\n{}", propsMapper.writeValueAsString(deployment.json()));
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        log.trace("\n{}", propsMapper.writeValueAsString(deployment.json()));
        log.trace("\n{}", mapper.writeValueAsString(deployment.json()));

        ApplicationProperties applicationProperties = ApplicationProperties.builder()
                .classpathPropertiesFile("application-defaults.properties")
                .testDefaults()
                .build();
        ApplicationJson applicationJson = new ApplicationJson(applicationProperties);
        log.trace("\n{}", mapper.writeValueAsString(applicationJson.json()));
    }

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

        ApplicationJson applicationJson = new ApplicationJson(props);

        Deployment deployment = new Deployment(ApplicationProperties.builder().build(), (ObjectNode) applicationJson.json());

        log.debug("\n{}", deployment.json().toPrettyString());
    }
}
