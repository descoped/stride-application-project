package io.descoped.stride.application.test.tuple;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TupleTest {

    private static final Logger log = LoggerFactory.getLogger(TupleTest.class);

    @Test
    void representationAndBuilder() {
        Tuple tuple = new TupleBuilder()
                .add("path")
                .add((byte) 8)
                .add(false)
                .add(100_000_000_000L)
                .add(true)
                .add(1)
                .pack();

        log.trace("size: {}", tuple.size());
        log.trace("{}", tuple.toString("/"));
        log.trace("tuple:\n{}", TupleHelper.printable(tuple));
    }

    @Test
    void sorted() {
        Tuple e1 = TupleHelper.from("/path/2/foo", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        Tuple e2 = TupleHelper.from("/path/3/bar", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        Tuple e4 = TupleHelper.from("/pat/1", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        Tuple e3 = TupleHelper.from("/path/1001", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);
        Tuple e5 = TupleHelper.from("/path/3/b0r", "/", PrimitiveType.STRING, PrimitiveType.INTEGER, PrimitiveType.STRING);

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

//        iterator.next();
        iterator.next();
        Tuple t = iterator.next();
        TupleBuilder builder = Tuple.unpack(t.getBytes());
        log.trace("---> {}", builder.elements.stream().map(TupleBuilder.ElementHolder::value).collect(Collectors.toList()));
        log.trace("printable: \n{}", TupleHelper.printable(t));
        assertArrayEquals(t.getBytes(), builder.pack().getBytes());

        Range range = t.range(Tuple.of("path", "2"), Tuple.of("path", "3"));
        // TODO info: 'range' represent inner/relative-path with elements
//        for (Tuple tuple : range.iterator()) {
//            // TODO traverse-tree
//        }
    }

    @Test
    void compare() {
        Tuple t1 = Tuple.of("a", "b");
        Tuple t2 = Tuple.of("a", "b");
        int cmp = t1.compareTo(t2);
        log.trace("cmp: {}", cmp);
    }
}
