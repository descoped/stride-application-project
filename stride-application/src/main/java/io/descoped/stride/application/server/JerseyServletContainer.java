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
        getApplicationHandler().onShutdown(this);
    }

    @Override
    public void destroy() {
        log.warn("Destroy Jersey servlet container");
    }
}
