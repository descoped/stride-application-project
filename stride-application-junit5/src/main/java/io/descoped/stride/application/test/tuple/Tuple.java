package io.descoped.stride.application.test.tuple;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Tuple {

    private static final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    private final DirectByteBufferPool byteBufferPool;

    public Tuple(DirectByteBufferPool byteBufferPool) {
        this.byteBufferPool = byteBufferPool;
    }

    public static byte[] from(DirectByteBufferPool byteBufferPool, String representation, String delimiter, PrimitiveType... typeMapping) {
        String[] elements = representation.replaceFirst("^" + delimiter, "").split(delimiter);
        TupleBuilder tupleBuilder = new TupleBuilder(byteBufferPool);
        for (int i = 0; i < elements.length; i++) {
            String element = elements[i];
            PrimitiveType type = typeMapping.length == 0 ? null : typeMapping[i];
            if (type == null) {
                tupleBuilder.add(element);
            } else {
                switch (type) {
                    case BYTE -> tupleBuilder.add(Byte.parseByte(element));
                    case BOOLEAN -> tupleBuilder.add(Boolean.TRUE.equals(Boolean.parseBoolean(element)));
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
        return tupleBuilder.build();
    }

    public List<Object> asList(byte[] representation) {
        List<Object> elements = new ArrayList<>();
        ByteBuffer byteBuffer = byteBufferPool.acquire();
        byteBuffer.clear();
        try {
            byteBuffer.put(representation, 0, representation.length);
            byteBuffer.flip();

            while (byteBuffer.position() < byteBuffer.limit()) {
                int offset = byteBuffer.getInt();
                PrimitiveType type = PrimitiveType.values()[byteBuffer.get()];

                switch (type) {
                    case BYTE -> {
                        byte b = byteBuffer.get();
                        elements.add(b);
                    }
                    case BOOLEAN -> {
                        int b = byteBuffer.get();
                        elements.add(b == 1 ? Boolean.TRUE : Boolean.FALSE);
                    }
                    case STRING -> {
                        // nothing to read
                        if (offset == 0) {
                            break;
                        }
                        ByteBuffer bb = ByteBuffer.allocateDirect(offset);
                        int numberOfBytesToRead = offset;
                        do {
                            bb.put(byteBuffer.get());
                        } while (--numberOfBytesToRead > 0);
                        bb.flip();

                        CharBuffer cb = CharBuffer.allocate((offset * 4) + 1);
                        decoder.reset();
                        CoderResult cr = decoder.decode(bb, cb, true);
                        if (cr.isError()) {
                            cr.throwException();
                        }
                        cb.flip();

                        elements.add(cb.toString());
                    }
                    case SHORT -> {
                        Short value = byteBuffer.getShort();
                        elements.add(value);
                    }
                    case INTEGER -> {
                        Integer value = byteBuffer.getInt();
                        elements.add(value);
                    }
                    case LONG -> {
                        Long value = byteBuffer.getLong();
                        elements.add(value);
                    }
                    case FLOAT -> {
                        Float value = byteBuffer.getFloat();
                        elements.add(value);
                    }
                    case DOUBLE -> {
                        Double value = byteBuffer.getDouble();
                        elements.add(value);
                    }
                    default -> throw new IllegalStateException();
                }
            }
            return elements;

        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        } finally {
            byteBufferPool.release(byteBuffer);
        }
    }

    public String toString(byte[] representation, CharSequence delimiter) {
        return delimiter + asList(representation)
                .stream()
                .map(Object::toString)
                .collect(Collectors.joining(delimiter));
    }
}
