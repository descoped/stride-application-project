package io.descoped.stride.application.test.server;

import io.descoped.stride.application.api.Logging;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import org.glassfish.hk2.api.ServiceLocator;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Random;

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
        try {
            URL url = new URL("http", application.getHost(), application.getLocalPort(), uri);
            return url.toExternalForm();
        } catch (MalformedURLException e) {
            throw new TestServerException(e);
        }
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

        public Builder() {
        }

        public Builder configuration(ApplicationConfiguration.Builder configurationBuilder) {
            this.configurationBuilder = configurationBuilder;
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
            if (configurationBuilder == null) {
                configurationBuilder = ApplicationConfiguration.builder()
                        .testDefaults();
            }

            if (host != null) {
                configurationBuilder.overrideProperty("services.jetty.server.config.host", host);
            }

            int pickPort = (port <= 0) ? findFreePort(new SecureRandom(), 9000, 9499) : port;
            configurationBuilder.overrideProperty("services.jetty.server.config.port", Integer.toString(pickPort));

            if (contextPath != null) {
                configurationBuilder.overrideProperty("services.jetty.server.config.context-path", contextPath);
            }

            ApplicationConfiguration configuration = configurationBuilder.build();
            StrideApplication app = StrideApplication.create(configuration);
            return new TestApplication(app);
        }
    }
}
