package io.descoped.stride.application.test.tuple;

import java.util.Iterator;

public interface Range {

    byte[] bytes();

    Tuple fromInclusive();

    Tuple toInclusive();

    default Tuple seek(Tuple tuple) {
        // TODO locate relative-path with elements. Find tuple as context.
        //  recurse how you
        /*
            How recursive func to createe iterator? :) This is the way (in this context)
            func(self) {
                // use Label, e.g `loop:`
                loop: loop() {
                    do stuff
                }
            }
         */
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
