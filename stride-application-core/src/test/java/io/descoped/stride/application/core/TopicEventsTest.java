package io.descoped.stride.application.core;

import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Services;
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
 * service receives in its {@link SubscribeTo} method. The subscriber is found by HK2's own discovery in one test
 * and named in the configuration in the other.
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

    static final List<OrderPlaced> BILLED = new CopyOnWriteArrayList<>();

    /** Named in the configuration instead of being discovered by HK2. */
    @MessageReceiver
    public static class Billing {
        public void on(@SubscribeTo OrderPlaced event) {
            BILLED.add(event);
        }
    }

    @Test
    void aSubscriberNamedInTheConfigurationKeepsItsQualifierAndReceivesEvents() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .services(Services.builder().service(io.descoped.stride.application.config.Service.builder("billing")
                        .clazz(Billing.class).runLevel(5)))
                .build();

        BILLED.clear();
        try (StrideApplication application = StrideApplication.create(configuration)) {
            application.start();

            application.getServiceLocator().getService(Orders.class).place("7");

            assertEquals(List.of(new OrderPlaced("7")), BILLED);
        }
    }

    @Test
    void aSubscriberReceivesWhatAnotherServicePublishes() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder().testDefaults().build();

        RECEIVED.clear();
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
