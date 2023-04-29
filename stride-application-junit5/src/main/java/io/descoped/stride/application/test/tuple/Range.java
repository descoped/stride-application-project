package io.descoped.stride.application.test.tuple;

import java.util.Iterator;

public interface Range {

    byte[] bytes();

    Tuple fromInclusive();

    Tuple toInclusive();

    default Tuple seek() {

        return null;
    }

    default Iterable<Tuple> iterator() {
        // TODO it over bytes
        Iterable<Tuple> it = new Iterable<Tuple>() {
            @Override
            public Iterator<Tuple> iterator() {
                return null;
            }
        };
        return null;
    }

    static Range newRange(byte[] bytes, Tuple fromInclusive, Tuple toInclusive) {
        record RangeImpl(byte[] bytes, Tuple fromInclusive, Tuple toInclusive) implements Range {
        }
        return new RangeImpl(bytes, fromInclusive, toInclusive);
    }

}
