package io.descoped.stride.application.test.tuple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Tuple {

    private static final Logger log = LoggerFactory.getLogger(Tuple.class);

    private final byte[] bytes;

    public Tuple(byte[] bytes) {
        Objects.requireNonNull(bytes);
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public byte[] getRawBytes() {
        if (bytes.length == 0) {
            return null;
        }

        ByteBuffer rawBuffer = ByteBuffer.allocate(bytes.length);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.position() < buffer.limit()) {
            buffer.get(); // skip type byte
            int offset = buffer.getInt(); // offset size

            byte[] payload = new byte[offset];
            for (int n = 0; n < offset; n++) {
                payload[n] = buffer.get();
            }
            rawBuffer.put(payload);
        }

        rawBuffer.flip();
        byte[] b = new byte[rawBuffer.remaining()];
        rawBuffer.get(b);
        return b;
    }

    public List<Element<?>> asList() {
        if (bytes.length == 0) {
            return null;
        }

        List<Element<?>> result = new ArrayList<>();

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.position() < buffer.limit()) {
            PrimitiveType type = PrimitiveType.values()[buffer.get()];
            int offset = buffer.getInt(); // offset size

            byte[] payload = new byte[offset];
            for (int n = 0; n < offset; n++) {
                payload[n] = buffer.get();
            }
            Element<?> element = switch (type) {
                case BOOLEAN -> new BooleanElement(payload);
                case BYTE -> new ByteElement(payload);
                case BYTE_ARRAY -> new ByteArrayElement(payload);
                case STRING -> new StringElement(payload);
                case SHORT -> new ShortElement(payload);
                case INTEGER -> new IntegerElement(payload);
                case LONG -> new LongElement(payload);
                case FLOAT -> new FloatElement(payload);
                case DOUBLE -> new DoubleElement(payload);
            };
            result.add(element);
        }

        return result;
    }

    public byte[] getRaw(int index) {
        if (bytes.length == 0 || index >= bytes.length) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int elementPosition = 0;
        while (buffer.position() < buffer.limit()) {
            buffer.get(); // skip type byte
            int offset = buffer.getInt(); // offset size

            if (elementPosition == index) {
                byte[] payload = new byte[offset];
                for (int n = 0; n < offset; n++) {
                    payload[n] = buffer.get();
                }
                return payload;
            }

            elementPosition++;
        }

        return null;
    }

    @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"})
    public <R> Element<R> get(int index) {
        if (bytes.length == 0 || index >= bytes.length) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int elementPosition = 0;
        while (buffer.position() < buffer.limit()) {
            PrimitiveType type = PrimitiveType.values()[buffer.get()];
            int offset = buffer.getInt(); // offset size
            int nextPosition = buffer.position() + offset;

            if (elementPosition == index) {
                byte[] payload = new byte[offset];
                for (int n = 0; n < offset; n++) {
                    payload[n] = buffer.get();
                }
                Element<R> element = (Element<R>) switch (type) {
                    case BOOLEAN -> new BooleanElement(payload);
                    case BYTE -> new ByteElement(payload);
                    case BYTE_ARRAY -> new ByteArrayElement(payload);
                    case STRING -> new StringElement(payload);
                    case SHORT -> new ShortElement(payload);
                    case INTEGER -> new IntegerElement(payload);
                    case LONG -> new LongElement(payload);
                    case FLOAT -> new FloatElement(payload);
                    case DOUBLE -> new DoubleElement(payload);
                };
                return element;
            }

            buffer.position(nextPosition);
            elementPosition++;
        }

        return null;
    }

    public int size() {
        if (bytes.length == 0) {
            return 0;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int elementPosition = 0;
        while (buffer.position() < buffer.limit()) {
            buffer.get(); // skip type byte
            int offset = buffer.getInt(); // offset size
            int nextPosition = buffer.position() + offset;
            buffer.position(nextPosition);
            elementPosition++;
        }

        return elementPosition;
    }

    public String toString(CharSequence delimiter) {
        return delimiter + asList()
                .stream()
                .map(Element::value)
                .map(Object::toString)
                .collect(Collectors.joining(delimiter));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple tuple = (Tuple) o;
        return Arrays.equals(bytes, tuple.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    // ---------------------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static <T> Tuple of(T... t) {
        TupleBuilder builder = new TupleBuilder();
        for (T e : t) {
            PrimitiveType type = PrimitiveType.typeOf(e);
            switch (type) {
                case BOOLEAN -> builder.add((Boolean) e);
                case BYTE -> builder.add((byte) e);
                case BYTE_ARRAY -> builder.add((byte[]) e);
                case STRING -> builder.add((String) e);
                case SHORT -> builder.add((Short) e);
                case INTEGER -> builder.add((Integer) e);
                case LONG -> builder.add((Long) e);
                case FLOAT -> builder.add((Float) e);
                case DOUBLE -> builder.add((Double) e);
            }
            ;
        }
        return builder.pack();
    }

    public static TupleBuilder unpack(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length == 0) {
            return null;
        }

        TupleBuilder builder = new TupleBuilder();

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.position() < buffer.limit()) {
            PrimitiveType type = PrimitiveType.values()[buffer.get()];
            int offset = buffer.getInt(); // offset size

            byte[] payload = new byte[offset];
            for (int n = 0; n < offset; n++) {
                payload[n] = buffer.get();
            }
            // TODO ugly code. Obviously needs refactoring.
            TupleBuilder.ElementHolder<?> element = switch (type) {
                case BOOLEAN -> new TupleBuilder.ObjectHolder(type, new BooleanElement(payload).value);
                case BYTE -> new TupleBuilder.ObjectHolder(type, new ByteElement(payload).value);
                case BYTE_ARRAY -> new TupleBuilder.ByteArrayHolder(payload);
                case STRING -> new TupleBuilder.StringHolder(new StringElement(payload).value);
                case SHORT -> new TupleBuilder.ObjectHolder(type, new ShortElement(payload).value);
                case INTEGER -> new TupleBuilder.ObjectHolder(type, new IntegerElement(payload).value);
                case LONG -> new TupleBuilder.ObjectHolder(type, new LongElement(payload).value);
                case FLOAT -> new TupleBuilder.ObjectHolder(type, new FloatElement(payload).value);
                case DOUBLE -> new TupleBuilder.ObjectHolder(type, new DoubleElement(payload).value);
            };
            builder.elements.add(element);
        }

        return builder;
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static final class StringDecoder {
        private static final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();

        synchronized static String utf8String(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            ByteBuffer bb = ByteBuffer.wrap(bytes);
            CharBuffer cb = CharBuffer.allocate(bytes.length);
            CoderResult cr = decoder.decode(bb, cb, true);
            try {
                if (cr.isError()) {
                    try {
                        cr.throwException();
                    } catch (CharacterCodingException e) {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                decoder.reset();
            }
            return cb.flip().toString();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    interface Element<T> {

        T value();

        byte[] toBytes();
    }

    abstract static class AbstractElement<T> implements Element<T> {
        protected final byte[] bytes;

        public AbstractElement(byte[] bytes) {
            Objects.requireNonNull(bytes);
            this.bytes = bytes;
        }

        @Override
        public byte[] toBytes() {
            return bytes;
        }
    }

    static class BooleanElement extends AbstractElement<Boolean> {
        private final Boolean value;

        public BooleanElement(byte[] bytes) {
            super(bytes);
            if (bytes.length != 1) {
                throw new IllegalArgumentException("Boolean bytes-buffer MUST BE 1 byte!");
            }
            value = bytes[0] == 1 ? Boolean.TRUE : Boolean.FALSE;
        }

        @Override
        public Boolean value() {
            return value;
        }
    }

    static class ByteElement extends AbstractElement<Byte> {

        private final byte value;

        public ByteElement(byte[] bytes) {
            super(bytes);
            if (bytes.length != 1) {
                throw new IllegalArgumentException("Byte bytes-buffer MUST BE 1 byte!");
            }
            value = bytes[0];
        }

        @Override
        public Byte value() {
            return value;
        }
    }

    static class ByteArrayElement extends AbstractElement<byte[]> {

        public ByteArrayElement(byte[] bytes) {
            super(bytes);
        }

        @Override
        public byte[] value() {
            return bytes;
        }
    }

    static class StringElement extends AbstractElement<String> {

        private final String value;

        public StringElement(byte[] bytes) {
            super(bytes);
            value = StringDecoder.utf8String(bytes);
        }

        @Override
        public String value() {
            return value;
        }
    }

    static class ShortElement extends AbstractElement<Short> {

        private final short value;

        public ShortElement(byte[] bytes) {
            super(bytes);
            if (bytes.length != 2) {
                throw new IllegalArgumentException("Short bytes-buffer MUST BE than 2 bytes!");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            value = buffer.getShort();
        }

        @Override
        public Short value() {
            return value;
        }
    }

    static class IntegerElement extends AbstractElement<Integer> {

        private final int value;

        public IntegerElement(byte[] bytes) {
            super(bytes);
            if (bytes.length != 4) {
                throw new IllegalArgumentException("Integer bytes-buffer MUST BE 4 bytes!");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            value = buffer.getInt();
        }

        @Override
        public Integer value() {
            return value;
        }
    }

    static class LongElement extends AbstractElement<Long> {

        private final long value;

        public LongElement(byte[] bytes) {
            super(bytes);
            if (bytes.length != 8) {
                throw new IllegalArgumentException("Long bytes-buffer MUST BE 8 bytes!");
            }
            ByteBuffer b = ByteBuffer.wrap(bytes);
            value = b.getLong();
        }

        @Override
        public Long value() {
            return value;
        }
    }

    static class FloatElement extends AbstractElement<Float> {

        private final float value;

        public FloatElement(byte[] bytes) {
            super(bytes);
            if (bytes.length != 4) {
                throw new IllegalArgumentException("Float bytes-buffer MUST BE 4 bytes!");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            value = buffer.getFloat();
        }

        @Override
        public Float value() {
            return value;
        }
    }

    static class DoubleElement extends AbstractElement<Double> {

        private final double value;

        public DoubleElement(byte[] bytes) {
            super(bytes);
            if (bytes.length != 8) {
                throw new IllegalArgumentException("Double bytes-buffer MUST BE  8 bytes!");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            value = buffer.getDouble();
        }

        @Override
        public Double value() {
            return value;
        }
    }
}
