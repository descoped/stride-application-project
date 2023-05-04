package io.descoped.stride.application.test.tuple;

import java.util.Comparator;

public class ByteArrayComparator implements Comparator<byte[]> {

    /**
     * Comparator<byte[]> implementation to sort keys that are byte arrays in ascending order based on their byte values.
     * The compare method of the Comparator implementation compares the byte values of corresponding bytes in the input
     * byte arrays left and right using bitwise AND operation with 0xff to convert them to unsigned integers.
     * If the corresponding byte values are not equal, the method returns the difference between the byte values.
     * If all the corresponding byte values are equal, the method returns the difference between the lengths of
     * the byte arrays left and right. This custom Comparator is used to determine the sorting order of keys
     * in the TreeMap.
     *
     * @param left  the first object to be compared.
     * @param right the second object to be compared.
     * @return assenting sorted order
     */
    @Override
    public int compare(byte[] left, byte[] right) {
        for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
            int a = (left[i] & 0xff);
            int b = (right[j] & 0xff);
            if (a != b) {
                return a - b;
            }
        }
        return left.length - right.length;
    }
}
