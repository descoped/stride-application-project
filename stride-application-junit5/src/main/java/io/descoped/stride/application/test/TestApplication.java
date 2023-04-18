package io.descoped.stride.application.test;

import io.descoped.stride.application.Application;
import io.descoped.stride.application.api.config.ApplicationConfiguration;
import io.descoped.stride.application.core.InstanceFactory;
import io.descoped.stride.application.core.Logging;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServlet;
import no.cantara.config.ApplicationProperties;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

public class TestApplication implements TestUriResolver {

    static {
        Logging.init();
    }

    static final Logger log = LoggerFactory.getLogger(TestApplication.class);
    private final Application application;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private TestApplication(Application application) {
        this.application = application;
    }

    public void start() {
        if (closed.compareAndSet(false, true)) {
            application.start();
        }
    }

    public void shutdown() {
        if (closed.compareAndSet(true, false)) {
            application.stop();
        }
    }

    @Override
    public String testURL(String uri) {
        try {
            URL url = new URL("http", application.getHost(), application.getPort(), uri);
            return url.toExternalForm();
        } catch (MalformedURLException e) {
            throw new TestServerException(e);
        }
    }

    public static class Builder {

        private ApplicationProperties.Builder applicationPropertiesBuilder;
        private final InstanceFactory instanceFactory;
        private String host = "localhost";
        private int port = -1;
        private String contextPath = "";
        private Map<String, Supplier<?>> instanceRegistryMap = new LinkedHashMap<>();
        private Map<String, HttpFilter> filtersMap = new LinkedHashMap<>();
        private Map<String, HttpServlet> servletsMap = new LinkedHashMap<>();
        private Map<String, Supplier<?>> jaxRsWsComponentMap = new LinkedHashMap<>();

        public Builder() {
            instanceFactory = new InstanceFactory();
        }

        public Builder properties(ApplicationProperties.Builder applicationPropertiesBuilder) {
            this.applicationPropertiesBuilder = applicationPropertiesBuilder;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder contextPath(String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        public Builder register(Class<?> clazz, Supplier<?> instance) {
            this.instanceRegistryMap.put(clazz.getName(), instance);
            return this;
        }

        public Builder register(String name, Supplier<?> instance) {
            this.instanceRegistryMap.put(name, instance);
            return this;
        }

        public Builder filter(String pathSpec, HttpFilter httpFilter) {
            this.filtersMap.put(pathSpec, httpFilter);
            return this;
        }

        public Builder servlet(String urlMapping, HttpServlet httpServlet) {
            this.servletsMap.put(urlMapping, httpServlet);
            return this;
        }

        public <R> Builder jaxRsWsComponent(Class<R> clazz,
                                            Function<InstanceFactory, Supplier<Object>> component) {
            this.jaxRsWsComponentMap.put(clazz.getName(), component.apply(instanceFactory));
            return this;
        }

        public <R> Builder jaxRsWsComponent(String key,
                                            Function<InstanceFactory, Supplier<?>> component) {
            this.jaxRsWsComponentMap.put(key, component.apply(instanceFactory));
            return this;
        }

        @SuppressWarnings("SameParameterValue")
        static int findFreePort(Random random, int from, int to) {
            int port = pick(random, from, to);
            for (int i = 0; i < 2 * ((to + 1) - from); i++) {
                if (isLocalPortFree(port)) {
                    return port;
                }
                port = pick(random, from, to);
            }
            throw new IllegalStateException(
                    "Unable to find any available ports in range: [" + from + ", " + (to + 1) + ")");
        }

        private static int pick(Random random, int from, int to) {
            return from + random.nextInt((to + 1) - from);
        }

        private static boolean isLocalPortFree(int port) {
            try {
                try (ServerSocket ignore = new ServerSocket(port)) {
                    return true;
                }
            } catch (IOException e) {
                return false;
            }
        }

        public TestApplication build() {
            int pickPort = port == -1 ? findFreePort(new SecureRandom(), 9000, 9499) : port;

            if (applicationPropertiesBuilder == null) {
                applicationPropertiesBuilder = ApplicationProperties.builder()
                        .testDefaults();
            }

            applicationPropertiesBuilder
                    .property("server.port", Integer.toString(pickPort))
                    .property("server.context-path", contextPath)
                    .build();

            ApplicationConfiguration configuration = ApplicationConfiguration.builder().testDefaults().build();
            Application app = new Application(configuration, instanceFactory);
            app.initBuiltinDefaults();

            // instance registry (internal to builder) is used to initialize internal InstanceFactory
            for (Map.Entry<String, Supplier<?>> entry : instanceRegistryMap.entrySet()) {
                app.init(entry.getKey(),
                        entry.getValue()); // todo Application must check that closable and runnable resources are closed
            }

            // filters
            for (Map.Entry<String, HttpFilter> entry : filtersMap.entrySet()) {
                app.initAndAddServletFilter(entry.getValue().getFilterName(), entry::getValue,
                        entry.getKey(), EnumSet.allOf(DispatcherType.class));
            }

            // servlets
            int n = 0;
            for (Map.Entry<String, HttpServlet> entry : servletsMap.entrySet()) {
                String servletName = "Servlet" + n++; // todo should be a user provided name
                app.init(servletName, entry::getValue);
                ServletContextHandler servletContextHandler = app.instanceFactory()
                        .getOrNull(ServletContextHandler.class);
                servletContextHandler.addServlet(new ServletHolder(entry.getValue()), entry.getKey());
            }

            // jaxrs resources
            for (Map.Entry<String, Supplier<?>> entry : jaxRsWsComponentMap.entrySet()) {
                Supplier<?> supplier = entry.getValue();
                app.initAndRegisterJaxRsWsComponent(entry.getKey(), supplier);
            }

            return new TestApplication(app);
        }
    }
}
