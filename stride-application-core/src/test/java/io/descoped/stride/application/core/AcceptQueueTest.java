package io.descoped.stride.application.core;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The listener's accept queue has the configured size. That a burst of new connections is then served, not reset,
 * is proven by {@code VirtualThreadsTest.moreRequestsBlockAtOnceThanTheServerHasPlatformThreads}, which opens 200
 * connections at once: more than the 50 a listener gets when no size is set.
 */
class AcceptQueueTest {

    private static int acceptQueueSizeOf(ApplicationConfiguration configuration) {
        try (StrideApplication application = StrideApplication.create(configuration)) {
            application.start();
            Server server = application.getServiceLocator().getService(Server.class);
            return ((ServerConnector) server.getConnectors()[0]).getAcceptQueueSize();
        }
    }

    @Test
    void theListenerHasTheDefaultAcceptQueueSize() {
        assertEquals(1024, acceptQueueSizeOf(ApplicationConfiguration.builder().testDefaults().build()));
    }

    @Test
    void theListenerHasTheConfiguredAcceptQueueSize() {
        assertEquals(77, acceptQueueSizeOf(ApplicationConfiguration.builder().testDefaults()
                .overrideProperty("services.jetty.server.config.acceptQueueSize", "77").build()));
    }
}
