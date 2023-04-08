package io.descoped.stride.application.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.config.Servlets;
import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletProperties;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Service(name = "jersey.server")
@RunLevel(RunLevelConstants.WEB_SERVER_RUN_LEVEL)
public class JerseyServerService implements PreDestroy {

    private static final Logger log = LoggerFactory.getLogger(JerseyServerService.class);

    private final JerseyServletContainer servletContainer;

    @SuppressWarnings("JavacQuirks")
    @Inject
    public JerseyServerService(ApplicationConfiguration configuration,
                               Filters filters,
                               Servlets servlets,
                               Resources resources,
                               ServletContextHandler servletContextHandler) throws ClassNotFoundException {

        ServiceLocator serviceLocator = (ServiceLocator) servletContextHandler.getAttribute(ServletProperties.SERVICE_LOCATOR);

        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.property(ServerProperties.WADL_FEATURE_DISABLE, Boolean.TRUE);
        resourceConfig.property(ServerProperties.UNWRAP_COMPLETION_STAGE_IN_WRITER_ENABLE, Boolean.TRUE);

        List<String> mediaTypesList = new ArrayList<>();
        configuration.with("services.jersey.server.config.mediaTypes").toMap(JsonNode::asText).forEach((key, mimeType) -> {
            mediaTypesList.add(key + ":" + mimeType);
        });
        String mediaTypesString = String.join(",", mediaTypesList);
        resourceConfig.property(ServerProperties.MEDIA_TYPE_MAPPINGS, mediaTypesString);

        // register filters
        for (io.descoped.stride.application.config.Filter filter : filters.iterator()) {
            Class<? extends Filter> filterClass = filter.clazz();
            Filter filterInstance = serviceLocator.createAndInitialize(filterClass);
            servletContextHandler.addFilter(new FilterHolder(filterInstance), filter.pathSpec(), filter.dispatches());
        }

        // register servlets
        for (io.descoped.stride.application.config.Servlet servlet : servlets.iterator()) {
            Class<? extends Servlet> servletClass = servlet.clazz();
            Servlet servletInstance = serviceLocator.createAndInitialize(servletClass);
            servletContextHandler.addServlet(new ServletHolder(servletInstance), servlet.pathSpec());
        }

        // register resources
        for (Resource resource : resources.iterator()) {
            String resourceClass = resource.className();
            try {
                resourceConfig.register(getClass().getClassLoader().loadClass(resourceClass));
                log.debug("Registered: {}", resourceClass);
            } catch (ClassNotFoundException e) {
                log.error("Could not load resource: {}", resourceClass, e);
                throw e;
            }
        }

        servletContainer = new JerseyServletContainer(resourceConfig);
        ServletHolder servletHolder = new ServletHolder(servletContainer);
        servletHolder.setInitOrder(1);
        servletContextHandler.addServlet(servletHolder, "/*");

        log.info("Jersey Servlet container started!");
    }

    @Override
    public void preDestroy() {
        servletContainer.stop();
    }
}
