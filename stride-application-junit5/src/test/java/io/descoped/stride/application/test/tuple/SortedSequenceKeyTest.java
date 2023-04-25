package io.descoped.stride.application.test.tuple;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.SortedSet;
import java.util.TreeSet;
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
    void representationAndBuilder() {
        TupleBuilder builder = new TupleBuilder(keyBufferPool)
                .add("path")
                .add((byte) 8)
                .add(false)
                .add(1);
        byte[] pathElements = builder.build();

        Tuple representation = new Tuple(keyBufferPool);

        log.trace("{}", pathElements);
        log.trace("{}", representation.asList(pathElements).stream().map(v -> v.toString() + " (" + v.getClass() + ")").collect(Collectors.toList()));
        log.trace("{}", representation.toString(pathElements, "/"));
    }

    @Test
    void sorted() {
        byte[] e1 = Tuple.from(keyBufferPool, "/path/2/foo", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        byte[] e2 = Tuple.from(keyBufferPool, "/path/3/bar", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        byte[] e4 = Tuple.from(keyBufferPool, "/pat/1", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        byte[] e3 = Tuple.from(keyBufferPool, "/path/1", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);

        SortedSet<byte[]> sortedSet = new TreeSet<>(new ByteArrayComparator());
        sortedSet.add(e1);
        sortedSet.add(e2);
        sortedSet.add(e4);
        sortedSet.add(e3);
        Tuple representation = new Tuple(keyBufferPool);
        for (byte[] bytes : sortedSet) {
            log.trace("{}", representation.toString(bytes, "/"));
        }
    }
}
