package io.descoped.stride.application.test.tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TupleBuilder {

    List<ElementHolder<?>> elements = new ArrayList<>();

    public TupleBuilder() {
    }

    public Tuple pack() {
        int elementsByteAllocation = elements.stream()
                .map(this::calculateByteAllocation)
                .mapToInt(Integer::intValue)
                .sum();

        ByteBuffer buffer = ByteBuffer.allocate(elementsByteAllocation);

        for (ElementHolder<?> element : elements) {
            boolean fixedTypeSize = element instanceof ObjectHolder;

            buffer.put((byte) element.type().ordinal()); // primitive type ordinal descriptor
            buffer.putInt(fixedTypeSize ? element.type().typeSize() / 8 : element.bytes().length); // payload element size

            switch (element.type()) {
                case BOOLEAN -> buffer.put((byte) (Boolean.TRUE.equals(element.value()) ? 1 : 0));
                case BYTE -> buffer.put((byte) element.value());
                case BYTE_ARRAY, STRING -> buffer.put(element.bytes());
                case SHORT -> buffer.putShort((Short) element.value());
                case INTEGER -> buffer.putInt((Integer) element.value());
                case LONG -> buffer.putLong((Long) element.value());
                case FLOAT -> buffer.putFloat((Float) element.value());
                case DOUBLE -> buffer.putDouble((Double) element.value());
            }
        }

        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return new Tuple(bytes);
    }

    private Integer calculateByteAllocation(ElementHolder<?> element) {
        boolean fixedTypeSize = element instanceof ObjectHolder;
        // byte array allocation map
        int byteAllocation = Byte.SIZE; // byte for storing PrimitiveType ordinal
        byteAllocation += Integer.SIZE; // placeholder for offset element length (payload)
        byteAllocation += fixedTypeSize ? element.type().typeSize() : element.bytes().length; // payload size
        return byteAllocation;
    }


    /*
        format: [tokenSize][token][tokenSize][token]
                [4]/[4]foo[4]/[7]foobar
     */

    public TupleBuilder add(byte element) {
        elements.add(new ObjectHolder(PrimitiveType.BYTE, element));
        return this;
    }

    public TupleBuilder add(byte[] element) {
        elements.add(new ByteArrayHolder(element));
        return this;
    }

    public TupleBuilder add(Boolean element) {
        elements.add(new ObjectHolder(PrimitiveType.BOOLEAN, element));
        return this;
    }

    public TupleBuilder add(String element) {
        elements.add(new StringHolder(element));
        return this;
    }

    public TupleBuilder add(Short element) {
        elements.add(new ObjectHolder(PrimitiveType.SHORT, element));
        return this;
    }

    public TupleBuilder add(Integer element) {
        elements.add(new ObjectHolder(PrimitiveType.INTEGER, element));
        return this;
    }

    public TupleBuilder add(Long element) {
        elements.add(new ObjectHolder(PrimitiveType.LONG, element));
        return this;
    }

    public TupleBuilder add(Float element) {
        elements.add(new ObjectHolder(PrimitiveType.FLOAT, element));
        return this;
    }

    public TupleBuilder add(Double element) {
        elements.add(new ObjectHolder(PrimitiveType.DOUBLE, element));
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TupleBuilder that = (TupleBuilder) o;
        return Objects.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements);
    }

    // ---------------------------------------------------------------------------------------------------------------

    public static Tuple from(String representation, String delimiter, PrimitiveType... typeMapping) {
        String[] elements = representation.replaceFirst("^" + delimiter, "").split(delimiter);

        TupleBuilder tupleBuilder = new TupleBuilder();

        for (int i = 0; i < elements.length; i++) {
            String element = elements[i];
            PrimitiveType type = typeMapping.length == 0 ? null : typeMapping[i];

            if (type == null) {
                tupleBuilder.add(element);
            } else {
                switch (type) {
                    case BOOLEAN -> tupleBuilder.add(Boolean.TRUE.equals(Boolean.parseBoolean(element)));
                    case BYTE -> tupleBuilder.add(Byte.parseByte(element));
                    case BYTE_ARRAY -> tupleBuilder.add(element.getBytes(StandardCharsets.UTF_8));
                    case STRING -> tupleBuilder.add(element);
                    case SHORT -> tupleBuilder.add(Short.parseShort(element));
                    case INTEGER -> tupleBuilder.add(Integer.parseInt(element));
                    case LONG -> tupleBuilder.add(Long.parseLong(element));
                    case FLOAT -> tupleBuilder.add(Float.parseFloat(element));
                    case DOUBLE -> tupleBuilder.add(Double.parseDouble(element));
                    default -> throw new IllegalStateException();
                }
            }
        }
        return tupleBuilder.pack();
    }

    // ---------------------------------------------------------------------------------------------------------------

    interface ElementHolder<T> {
        PrimitiveType type();

        T value();

        byte[] bytes();
    }

    static class ByteArrayHolder implements ElementHolder<byte[]> {
        private final byte[] bytes;

        ByteArrayHolder(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public PrimitiveType type() {
            return PrimitiveType.BYTE_ARRAY;
        }

        @Override
        public byte[] value() {
            return bytes;
        }

        @Override
        public byte[] bytes() {
            return bytes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ByteArrayHolder that = (ByteArrayHolder) o;
            return Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    static class StringHolder implements ElementHolder<String> {
        private final String value;
        private final byte[] bytes;

        StringHolder(String value) {
            this.value = value;
            bytes = value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public PrimitiveType type() {
            return PrimitiveType.STRING;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public byte[] bytes() {
            return bytes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringHolder that = (StringHolder) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    static class ObjectHolder implements ElementHolder<Object> {
        private final PrimitiveType type;
        private final Object value;

        ObjectHolder(PrimitiveType type, Object value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public PrimitiveType type() {
            return type;
        }

        @Override
        public Object value() {
            return value;
        }

        @Override
        public byte[] bytes() {
            throw new UnsupportedOperationException("This method should only be called for specialized non-fixed primitive types");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ObjectHolder that = (ObjectHolder) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
