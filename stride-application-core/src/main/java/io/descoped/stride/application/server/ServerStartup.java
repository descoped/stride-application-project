package io.descoped.stride.application.server;

import jakarta.inject.Inject;
import org.eclipse.jetty.server.Server;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;

/** Bind the listener only after web components at level 10 are ready. */
@Service(name = "jetty.start")
@RunLevel(RunLevelConstants.WEB_SERVER_RUN_LEVEL + 1)
public final class ServerStartup implements PreDestroy {
    private final Server server;

    @Inject public ServerStartup(ServiceLocator locator) throws Exception {
        server = locator.getService(Server.class);
        if (server == null) throw new IllegalStateException("Jetty server has not been assembled");
        server.start();
    }

    @Override public void preDestroy() {
        try { server.stop(); }
        catch (Exception e) { throw new IllegalStateException("Could not stop Jetty", e); }
    }
}
