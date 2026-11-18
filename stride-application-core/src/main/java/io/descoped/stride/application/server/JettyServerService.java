package io.descoped.stride.application.server;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
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
import org.eclipse.jetty.server.handler.GracefulHandler;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.IterableProvider;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.jersey.servlet.ServletProperties;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service(name = "jetty.server")
@RunLevel(RunLevelConstants.WEB_SERVER_RUN_LEVEL)
public class JettyServerService implements Factory<ServletContextHandler>, PreDestroy {

    private static final Logger log = LoggerFactory.getLogger(JettyServerService.class);
    /**
     * The context attribute that holds the child locator the servlet context initializers put their services in.
     * The Jersey service reads it from the handler it is given, to look resource arguments up there.
     */
    static final String WEB_SERVICE_LOCATOR = "io.descoped.stride.application.server.webServiceLocator";
    private final Server server;
    private final ServletContextHandler servletContextHandler;
    private final ServiceLocator webServiceLocator;

    @Inject
    public JettyServerService(ApplicationConfiguration configuration,
                              Components components,
                              ServiceLocator serviceLocator,
                              IterableProvider<SecurityHandler> securityHandlerProvider) throws Exception {

        ApplicationConfiguration.Server jettyConfig = configuration.server();

        // Platform threads accept and select; every request is handled on a new virtual thread.
        QueuedThreadPool threadPool = new QueuedThreadPool(jettyConfig.maxThreads(), jettyConfig.minThreads());
        threadPool.setName("stride-jetty");
        threadPool.setVirtualThreadsExecutor(VirtualThreads.perTaskExecutor("stride-http-"));

        server = new Server(threadPool);

        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setOutputBufferSize(jettyConfig.outputBufferSize());
        httpConfig.setRequestHeaderSize(jettyConfig.requestHeaderSize());

        // Added for X-Forwarded-For support, from ALB
        if (configuration.requireBoolean("services.jetty.server.config.forwarded.enabled")) {
            httpConfig.addCustomizer(new ForwardedRequestCustomizer());
        }

        HttpConnectionFactory http11 = new HttpConnectionFactory(httpConfig);
        ServerConnector httpConnector;
        if (jettyConfig.isHttp2Enabled()) {
            // The ConnectionFactory for clear-text HTTP/2.
            HTTP2CServerConnectionFactory h2c = new HTTP2CServerConnectionFactory(httpConfig);

            // Create and configure the HTTP 1.1/2 connector
            httpConnector = new ServerConnector(server, http11, h2c);
        } else {
            // Create and configure the HTTP 1.1 connector
            httpConnector = new ServerConnector(server, http11);
        }
        httpConnector.setIdleTimeout(jettyConfig.idleTimeout());
        httpConnector.setAcceptQueueSize(jettyConfig.acceptQueueSize());
        httpConnector.setHost(jettyConfig.host());
        httpConnector.setPort(jettyConfig.port());
        server.addConnector(httpConnector);

        Slf4jRequestLogWriter requestLog = new Slf4jRequestLogWriter();
        requestLog.setLoggerName("jetty");
        server.setRequestLog(new CustomRequestLog(requestLog, "%{client}a - %u %t \"%r\" %s %O \"%{Referer}i\" \"%{User-Agent}i\""));

        servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setAttribute(ServletProperties.SERVICE_LOCATOR, serviceLocator);
        servletContextHandler.setContextPath(jettyConfig.contextPath());

        // Filters belong to the server, not to Jersey: they apply to every servlet, and to /ping, whether or not
        // the Jersey service is enabled.
        for (FilterRegistrations.Registration registration : FilterRegistrations.collect(configuration, components, serviceLocator)) {
            servletContextHandler.addFilter(new FilterHolder(registration.filter()), registration.pathSpec(), registration.dispatches());
            log.debug("Filter {} (order {}) on {}", registration.name(), registration.order(), registration.pathSpec());
        }

        // ping servlet
        ServletHolder pingServletHolder = new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                resp.setStatus(200);
            }
        });
        servletContextHandler.addServlet(pingServletHolder, "/ping");

        // initializers and servlets, after the filters and before Jersey takes the remaining paths
        webServiceLocator = ServiceLocatorFactory.getInstance().create(null, serviceLocator);
        try {
            new ServletRegistrations(serviceLocator, webServiceLocator, servletContextHandler).register(configuration, components);
        } catch (RuntimeException e) {
            webServiceLocator.shutdown(); // preDestroy does not run for a service whose constructor failed
            throw e;
        }
        servletContextHandler.setAttribute(WEB_SERVICE_LOCATOR, webServiceLocator);

        Handler handler = servletContextHandler;
        if (securityHandlerProvider.getHandle() != null) {
            SecurityHandler securityHandler = securityHandlerProvider.get();
            securityHandler.setHandler(handler);
            handler = securityHandler;
        }

        // On stop, no new requests are accepted and those already running get the stop timeout to finish. Requests
        // run on virtual threads that no pool joins, so without this they would be cut off mid-response.
        server.setHandler(new GracefulHandler(handler));
        server.setStopTimeout(jettyConfig.stopTimeout());
        // The listener starts at the next run level, after Jersey and filters are assembled.

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
        } catch (Exception e) {
            log.error("Could not stop Jetty server", e);
        }
        if (!webServiceLocator.isShutdown()) webServiceLocator.shutdown();
    }
}
