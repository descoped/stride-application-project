package io.descoped.stride.application;

import no.cantara.config.ApplicationProperties;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class StrideApplicationTest {

    private static final Logger log = LoggerFactory.getLogger(StrideApplicationTest.class);

    @Test
    void testBootstrap() {
        ApplicationProperties properties = ApplicationProperties.builder()
                .classpathPropertiesFile("application-defaults.properties")
                .testDefaults()
                .build();

        try (StrideApplication application = new StrideApplication(properties)) {
            application.start();
//            int httpPort = getHttpPort(application.getServiceLocator().getService(Server.class));
        }
    }

    public int getHttpPort(Server server) {
        int port = -3;
        for (Connector connector : server.getConnectors()) {
            // the first connector should be the http connector
            ServerConnector serverConnector = (ServerConnector) connector;
            List<String> protocols = serverConnector.getProtocols();
            if (!protocols.contains("ssl") && (protocols.contains("http/1.1") || protocols.contains("h2c"))) {
                port = serverConnector.getLocalPort();
                break;
            }
        }
        return port;
    }

    public int getHttpsPort(Server server) {
        int port = -3;
        for (Connector connector : server.getConnectors()) {
            // the first connector should be the http connector
            ServerConnector serverConnector = (ServerConnector) connector;
            List<String> protocols = serverConnector.getProtocols();
            if (protocols.contains("ssl") && (protocols.contains("http/1.1") || protocols.contains("h2"))) {
                port = serverConnector.getLocalPort();
                break;
            }
        }
        return port;
    }

}
