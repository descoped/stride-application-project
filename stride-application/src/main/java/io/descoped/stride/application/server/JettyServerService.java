package io.descoped.stride.application.server;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.JsonElement;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

@Service(name = "jetty.server")
@RunLevel(4)
public class JettyServerService implements Factory<ServletContextHandler>, PreDestroy {

    private static final Logger log = LoggerFactory.getLogger(JettyServerService.class);
    private final Server server;
    private final ServletContextHandler servletContextHandler;

    @Inject
    public JettyServerService(ApplicationConfiguration configuration,
                              ServiceLocator serviceLocator,
                              IterableProvider<SecurityHandler> securityHandlerProvider) throws Exception {

        JsonElement jettyServerConfig = configuration.server().element().with("jetty.server");

        int httpPort = configuration.server().port();

        JettyConnectorThreadPool jettyConnectorThreadPool = new JettyConnectorThreadPool();
        jettyConnectorThreadPool.setName("jetty-http-server-");
        jettyConnectorThreadPool.setMinThreads(jettyServerConfig.with("minThreads").asInt(10));
        jettyConnectorThreadPool.setMaxThreads(jettyServerConfig.with("maxThreads").asInt(150));

        server = new Server(jettyConnectorThreadPool);

        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setOutputBufferSize(jettyServerConfig.with("outputBufferSize").asInt(32768));
        httpConfig.setRequestHeaderSize(jettyServerConfig.with("requestHeaderSize").asInt(16384));

        // Added for X-Forwarded-For support, from ALB
        httpConfig.addCustomizer(new ForwardedRequestCustomizer());

        HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);
        ServerConnector httpConnector;
        if (jettyServerConfig.with("http2.enabled").asBoolean(false)) {
            // The ConnectionFactory for clear-text HTTP/2.
            HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpConfig);

            // Create and configure the HTTP 1.1/2 connector
            httpConnector = new ServerConnector(server, http11, h2c);
        } else {
            // Create and configure the HTTP 1.1 connector
            httpConnector = new ServerConnector(server, http11);
        }
        httpConnector.setIdleTimeout(Duration.parse(jettyServerConfig.with("idleTimeout").asString("PT-1s")).toSeconds());
        httpConnector.setPort(httpPort);
        server.addConnector(httpConnector);

        Slf4jRequestLogWriter requestLog = new Slf4jRequestLogWriter();
        requestLog.setLoggerName("jetty");
        server.setRequestLog(new CustomRequestLog(requestLog, "%{client}a - %u %t \"%r\" %s %O \"%{Referer}i\" \"%{User-Agent}i\""));

        servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setAttribute("jersey.config.servlet.context.serviceLocator", serviceLocator);
        servletContextHandler.setContextPath("/");

        JettyWebSocketServletContainerInitializer.configure(servletContextHandler, null);

        // ping servlet
        ServletHolder pingServletHolder = new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                resp.setStatus(200);
            }
        });
        servletContextHandler.addServlet(pingServletHolder, "/ping");

        Handler handler = servletContextHandler;
        if (securityHandlerProvider.getHandle() != null) {
            SecurityHandler securityHandler = securityHandlerProvider.get();
            securityHandler.setHandler(handler);
            handler = securityHandler;
        }

        server.setHandler(handler);
        server.start();

        ServiceLocatorUtilities.addOneConstant(serviceLocator, server);
    }

    @Singleton
    @Named("jetty.server")
    @Override
    public ServletContextHandler provide() {
        return servletContextHandler;
    }

    @Override
    public void dispose(ServletContextHandler instance) {
        // ignore
    }

    @Override
    public void preDestroy() {
        log.info("Stopping Jetty server");
        try {
            server.stop();
        } catch (Throwable e) {
            log.error("{}", captureStackTrace(e));
        }
    }

    static String captureStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}
