package io.descoped.stride.application.core;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import jakarta.inject.Inject;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.glassfish.hk2.api.messaging.Topic;
import org.junit.jupiter.api.Test;
import org.jvnet.hk2.annotations.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HK2 topic events are enabled in a started application: what one service publishes on a {@link Topic}, another
 * service receives in its {@link SubscribeTo} method. Both services are found by HK2's own discovery.
 */
class TopicEventsTest {

    public record OrderPlaced(String order) {
    }

    static final List<OrderPlaced> RECEIVED = new CopyOnWriteArrayList<>();

    @Service
    public static class Orders {
        private final Topic<OrderPlaced> placed;

        @Inject
        public Orders(Topic<OrderPlaced> placed) {
            this.placed = placed;
        }

        void place(String order) {
            placed.publish(new OrderPlaced(order));
        }
    }

    @Service
    @MessageReceiver
    public static class Shipping {
        public void on(@SubscribeTo OrderPlaced event) {
            RECEIVED.add(event);
        }
    }

    @Test
    void aSubscriberReceivesWhatAnotherServicePublishes() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder().testDefaults().build();

        try (StrideApplication application = StrideApplication.create(configuration)) {
            application.start();
            ServiceLocator locator = application.getServiceLocator();
            // a subscriber receives events from the moment it exists
            locator.getService(Shipping.class);

            locator.getService(Orders.class).place("42");

            assertEquals(List.of(new OrderPlaced("42")), RECEIVED);
        }
    }
}
