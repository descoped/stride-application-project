package io.descoped.stride.application.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.config.Servlets;
import io.descoped.stride.application.jackson.JsonElement;
import jakarta.inject.Inject;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
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

    @Inject
    public JerseyServerService(ApplicationConfiguration configuration,
                               Filters filters,
                               Servlets servlets,
                               ServletContextHandler ctx) throws ClassNotFoundException {

        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.property(ServerProperties.WADL_FEATURE_DISABLE, Boolean.TRUE);
        resourceConfig.property(ServerProperties.UNWRAP_COMPLETION_STAGE_IN_WRITER_ENABLE, Boolean.TRUE);

        List<String> mediaTypesList = new ArrayList<>();
        configuration.with("jersey.server.mediaTypes").toMap(JsonNode::asText).forEach((key, mimeType) -> {
            mediaTypesList.add(key + ":" + mimeType);
        });
        String mediaTypesString = String.join(",", mediaTypesList);
        resourceConfig.property(ServerProperties.MEDIA_TYPE_MAPPINGS, mediaTypesString);

        JsonElement resourceClassArray = configuration.with("jersey.server.register");
        if (resourceClassArray.json().isArray() && !resourceClassArray.isEmpty()) {
            for (JsonNode jsonNode : resourceClassArray.array()) {
                String resourceClass = jsonNode.asText();
                try {
                    resourceConfig.register(getClass().getClassLoader().loadClass(resourceClass));
                    log.debug("Registered: {}", resourceClass);
                } catch (ClassNotFoundException e) {
                    log.error("Could not load resource: {}", resourceClass, e);
                    throw e;
                }
            }
        }

        servletContainer = new JerseyServletContainer(resourceConfig);
        ServletHolder servletHolder = new ServletHolder(servletContainer);
        servletHolder.setInitOrder(1);
        ctx.addServlet(servletHolder, "/*");

        log.info("Jersey Servlet container started!");
    }

    @Override
    public void preDestroy() {
        servletContainer.stop();
    }
}
