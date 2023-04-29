package io.descoped.stride.application.test.tuple;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SortedSequenceKeyTest {

    private static final Logger log = LoggerFactory.getLogger(SortedSequenceKeyTest.class);

    @Test
    void representationAndBuilder() {
        Tuple tuple = new TupleBuilder()
                .add("path")
                .add((byte) 8)
                .add(false)
                .add(100L)
                .add(true)
                .add(1)
                .pack();

        log.trace("bytes: {}", tuple.getBytes());
        log.trace("rawBytes: {}", tuple.getRawBytes());
        log.trace("size: {}", tuple.size());
        log.trace("get 1: {}", tuple.<String>get(0).value());
        log.trace("get 2: {}", tuple.get(1).value());
        log.trace("get 2: {}", tuple.get(2).value());
        log.trace("get 3: {}", tuple.get(3).value());
        log.trace("get 4: {}", tuple.get(4).value());
        log.trace("get 5: {}", tuple.get(5).value());

        log.trace("{}", tuple.asList().stream().map(v -> v.value().toString() + " (" + v.value().getClass() + ")").collect(Collectors.toList()));
        log.trace("{}", tuple.toString("/"));
    }

    @Test
    void sorted() {
        Tuple e1 = TupleBuilder.from("/path/2/foo", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        Tuple e2 = TupleBuilder.from("/path/3/bar", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        Tuple e4 = TupleBuilder.from("/pat/1", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        Tuple e3 = TupleBuilder.from("/path/1001", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        Tuple e5 = TupleBuilder.from("/path/3/b0r", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);

        SortedSet<Tuple> sortedSet = new TreeSet<>(new TupleComparator());
        sortedSet.add(e1);
        sortedSet.add(e2);
        sortedSet.add(e4);
        sortedSet.add(e3);
        sortedSet.add(e5);

        for (Tuple tuple : sortedSet) {
            log.trace("{}", tuple.toString("/"));
        }
    }

    @Test
    void sorted2() {
        SortedSet<Tuple> sortedSet = new TreeSet<>(new TupleComparator());
        sortedSet.add(Tuple.of("path", 1));
        sortedSet.add(Tuple.of("path", 2, "foo")); // (*) 2 comes before string 2 because int sorts before string
        sortedSet.add(Tuple.of("path", "3", "bar"));
        sortedSet.add(Tuple.of("path", "3", "b0r"));
        sortedSet.add(Tuple.of("path", "2", "bar")); // comes after (*)
        sortedSet.forEach(t -> log.trace("{}", t.toString("/")));

        Iterator<Tuple> iterator = sortedSet.iterator();
        iterator.next();
        iterator.next();
        Tuple t = iterator.next();
        TupleBuilder builder = Tuple.unpack(t.getBytes());
        log.trace("---> {}", builder.elements.stream().map(e -> e.value()).collect(Collectors.toList()));
        assertArrayEquals(t.getBytes(), builder.pack().getBytes());

    }
}
