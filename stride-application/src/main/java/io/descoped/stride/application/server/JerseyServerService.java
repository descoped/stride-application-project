package io.descoped.stride.application.server;

import io.descoped.stride.application.config.ApplicationConfiguration;
import jakarta.inject.Inject;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;

@Service(name = "jersey.server")
@RunLevel(4)
public class JerseyServerService implements PreDestroy {

//    private final JerseyServletContainer servletContainer;

    @Inject
    public JerseyServerService(ApplicationConfiguration configuration,
                               ServletContextHandler ctx) throws Exception {


    }

    @Override
    public void preDestroy() {

    }
}
