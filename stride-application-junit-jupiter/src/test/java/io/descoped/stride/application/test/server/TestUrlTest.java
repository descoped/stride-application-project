package io.descoped.stride.application.test.server;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestUrlTest {

    @Test
    void anIpv6HostIsBracketedOnce() {
        assertEquals("http://localhost:8080/ping", TestApplication.testURL("localhost", 8080, "/ping"));
        assertEquals("http://[::1]:8080/ping?a=b", TestApplication.testURL("::1", 8080, "/ping?a=b"));
        assertEquals("http://[::1]:8080/ping", TestApplication.testURL("[::1]", 8080, "/ping"));
        assertEquals("::1", URI.create(TestApplication.testURL("::1", 8080, "/ping")).getHost().replaceAll("[\\[\\]]", ""));
    }
}
