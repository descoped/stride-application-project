package io.descoped.stride.application.server;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JerseyServletContainer extends ServletContainer {

    private static final Logger log = LoggerFactory.getLogger(JerseyServletContainer.class);

    public JerseyServletContainer(ResourceConfig resourceConfig) {
        super(resourceConfig);
    }

    public void stop() {
        // Jetty owns ServletContainer.destroy() and invokes it when the context stops.
    }

    @Override
    public void destroy() {
        super.destroy();
        log.debug("Destroyed Jersey servlet container");
    }
}
