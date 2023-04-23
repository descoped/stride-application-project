package io.descoped.stride.application.test;

import io.descoped.stride.application.api.Logging;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.api.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestApplication implements TestUriResolver {

    static {
        Logging.init();
    }

    static final Logger log = LoggerFactory.getLogger(TestApplication.class);
    private final StrideApplication application;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private TestApplication(StrideApplication application) {
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
            URL url = new URL("http", application.getHost(), application.getLocalPort(), uri);
            return url.toExternalForm();
        } catch (MalformedURLException e) {
            throw new TestServerException(e);
        }
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

            int pickPort = (port == -1) ? findFreePort(new SecureRandom(), 9000, 9499) : port;
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
