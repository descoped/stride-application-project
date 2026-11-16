package io.descoped.stride.application.test.server;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.api.Logging;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import org.glassfish.hk2.api.ServiceLocator;


public class TestApplication implements TestUriResolver, StrideApplication {

    static {
        Logging.init();
    }

    private final StrideApplication application;

    private TestApplication(StrideApplication application) {
        this.application = application;
    }

    @Override
    public void activate() {
        application.activate();
    }

    @Override
    public void proceedTo(int runLevel) {
        application.proceedTo(runLevel);
    }

    @Override
    public void start() {
        application.start();
    }

    @Override
    public void stop() {
        application.stop();
    }

    @Override
    public boolean isRunning() {
        return application.isRunning();
    }

    @Override
    public boolean isCompleted() {
        return application.isCompleted();
    }

    @Override
    public ServiceLocator getServiceLocator() {
        return application.getServiceLocator();
    }

    @Override
    public String getHost() {
        return application.getHost();
    }

    @Override
    public int getLocalPort() {
        return application.getLocalPort();
    }

    @Override
    public int getPort() {
        return application.getPort();
    }

    @Override
    public String testURL(String uri) {
        String host = application.getHost();
        return "http://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + application.getLocalPort() + uri;
    }

    @Override
    public void close() {
        application.close();
    }

    public static class Builder {

        private ApplicationConfiguration.Builder configurationBuilder;
        private String host = "localhost";
        private int port = -1;
        private String contextPath = "";
        private Components components = Components.none();

        public Builder() {
        }

        public Builder configuration(ApplicationConfiguration.Builder configurationBuilder) {
            this.configurationBuilder = configurationBuilder;
            return this;
        }

        /**
         * Ready-made objects for the application under test, for example a resource wired to a fake collaborator.
         */
        public Builder components(Components components) {
            this.components = components;
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

        public TestApplication build() {
            if (configurationBuilder == null) {
                configurationBuilder = ApplicationConfiguration.builder()
                        .testDefaults();
            }

            if (host != null) {
                configurationBuilder.overrideProperty("services.jetty.server.config.host", host);
            }

            int pickPort = Math.max(port, 0);
            configurationBuilder.overrideProperty("services.jetty.server.config.port", Integer.toString(pickPort));

            if (contextPath != null) {
                configurationBuilder.overrideProperty("services.jetty.server.config.context-path", contextPath);
            }

            ApplicationConfiguration configuration = configurationBuilder.build();
            StrideApplication app = StrideApplication.create(configuration, components);
            return new TestApplication(app);
        }
    }
}
