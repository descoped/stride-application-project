package io.descoped.stride.application.core;

import io.descoped.stride.application.cors.ApplicationCORSServletFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsFilterSettingsTest {

    // Fetch standard, CORS protocol: a response to a credentialed request cannot allow every origin with "*"
    @Test
    void credentialsCannotBeAllowedForEveryOrigin() {
        ApplicationCORSServletFilter.Builder builder = ApplicationCORSServletFilter.builder()
                .origin("*")
                .credentials("true");

        assertThrows(IllegalArgumentException.class, builder::build);
    }
}
