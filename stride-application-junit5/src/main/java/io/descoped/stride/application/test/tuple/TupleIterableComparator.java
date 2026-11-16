package io.descoped.stride.application.test.tuple;

import java.util.Comparator;
import java.util.Iterator;

public class TupleIterableComparator implements Comparator<Iterable<byte[]>> {

    static final ByteArrayComparator comparator = new ByteArrayComparator();

    @Override
    public int compare(Iterable<byte[]> iterable1, Iterable<byte[]> iterable2) {
        Iterator<byte[]> i1 = iterable1.iterator();
        Iterator<byte[]> i2 = iterable2.iterator();

        while (i1.hasNext() && i2.hasNext()) {
            int itemComp = comparator.compare(i1.next(), i2.next()); // fdb tuple compare bytes
            if (itemComp != 0) {
                return itemComp;
            }
        }

        if (i1.hasNext()) {
            // iterable2 is a prefix of iterable1.
            return 1;
        }
        if (i2.hasNext()) {
            // iterable1 is a prefix of iterable2.
            return -1;
        }
        return 0;
    }
}
