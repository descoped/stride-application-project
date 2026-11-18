package io.descoped.stride.application.test.server;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeoutSettingsTest {

    @Test
    void aTimeoutMustBePositiveWhereItIsGiven() {
        StrideExtension.Builder builder = StrideExtension.builder();
        for (Duration invalid : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertThrows(IllegalArgumentException.class, () -> TestClient.create(uri -> uri, invalid));
            assertThrows(IllegalArgumentException.class, () -> builder.timeout(invalid));
        }
        assertThrows(NullPointerException.class, () -> builder.timeout(null));
    }

    @Test
    void aClientKeepsTheTimeoutItWasGiven() {
        try (TestClient client = TestClient.create(uri -> uri, Duration.ofSeconds(2))) {
            assertEquals(Duration.ofSeconds(2), client.timeout());
        }
        try (TestClient client = TestClient.create(uri -> uri)) {
            assertEquals(TestClient.DEFAULT_TIMEOUT, client.timeout());
        }
    }
}
