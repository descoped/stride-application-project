package io.descoped.stride.application.test.server.utils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ByteArrayRepresentation implements AutoCloseable {

    private static final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    private final DirectByteBufferPool byteBufferPool;
    private final ByteBuffer byteBuffer;

    public ByteArrayRepresentation(DirectByteBufferPool byteBufferPool, ByteBuffer representation) {
        this.byteBufferPool = byteBufferPool;
        this.byteBuffer = representation;
    }

    public ByteArrayRepresentation(DirectByteBufferPool byteBufferPool, byte[] representation) {
        this.byteBufferPool = byteBufferPool;
        byteBuffer = byteBufferPool.acquire();
        byteBuffer.put(representation, 0, representation.length);
        byteBuffer.flip();
    }

    public List<Object> toElements() {
        List<Object> elements = new ArrayList<>();
        try {
            int offset;
            while (byteBuffer.position() < byteBuffer.limit()) {
                offset = byteBuffer.getInt();
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
            byteBuffer.flip();
        }
    }

    public String toString(CharSequence delimiter) {
        return delimiter + toElements().stream().map(Object::toString).collect(Collectors.joining(delimiter));
    }

    public String toString() {
        return toString("_");
    }

    @Override
    public void close() {
        byteBufferPool.release(byteBuffer);
    }
}
