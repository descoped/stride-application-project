package io.descoped.stride.application.test.server.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

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
    void testBuffers() throws CharacterCodingException {
        String s = "foo";
        ByteBuffer bb = ByteBuffer.allocateDirect(s.length());
        bb.put(s.getBytes(StandardCharsets.UTF_8));
        bb.flip();

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        CharBuffer cb = CharBuffer.allocate(bb.capacity());
        CoderResult cr = decoder.decode(bb, cb, true);
        if (cr.isError()) {
            cr.throwException();
        }
        log.trace("=> {}", cb.flip().toString());

//        byte[] bytes = new byte[bb.remaining()];
//        bb.get(bytes);
//        log.trace("{}", new String(bytes));
    }

    @Test
    void testSortedKeys() {
        ByteArrayBuilder builder = new ByteArrayBuilder(keyBufferPool);
        builder.add("path");
        builder.add("1");
        byte[] pathElements = builder.build();
        log.trace("{}", pathElements);
        log.trace("{}", new String(pathElements));
        log.trace("{}", ByteArrayBuilder.toElements(keyBufferPool, pathElements));
    }
}
