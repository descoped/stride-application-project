package io.descoped.stride.application.test.server.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

class SortedSequenceKeyTest {

    private static final Logger log = LoggerFactory.getLogger(SortedSequenceKeyTest.class);
    private static DirectByteBufferPool keyBufferPool;

    @BeforeAll
    static void beforeAll() {
        keyBufferPool = new DirectByteBufferPool(1000, 511);
    }

    @AfterAll
    static void afterAll() {
        keyBufferPool.close();
    }

    @Test
    void testSortedKeys() {
        ByteArrayBuilder builder = new ByteArrayBuilder(keyBufferPool)
                .add("path")
                .add((byte) 8)
                .add(false)
                .add(1);
        byte[] pathElements = builder.build();

        ByteArrayRepresentation representation = new ByteArrayRepresentation(keyBufferPool, pathElements);

        log.trace("{}", pathElements);
        log.trace("{}", representation.toElements().stream().map(v -> v.toString() + " (" + v.getClass() + ")").collect(Collectors.toList()));
        log.trace("{}", representation.toString("/"));
    }
}
