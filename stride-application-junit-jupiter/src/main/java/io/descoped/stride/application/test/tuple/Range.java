package io.descoped.stride.application.test.tuple;

public interface Range {

    byte[] bytes();

    Tuple fromInclusive();

    Tuple toInclusive();

    static Range newRange(byte[] bytes, Tuple fromInclusive, Tuple toInclusive) {
        record RangeImpl(byte[] bytes, Tuple fromInclusive, Tuple toInclusive) implements Range {
        }
        return new RangeImpl(bytes, fromInclusive, toInclusive);
    }

}
