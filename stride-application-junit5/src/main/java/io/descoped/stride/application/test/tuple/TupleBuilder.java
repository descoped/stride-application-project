package io.descoped.stride.application.test.tuple;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TupleBuilder {

    private final DirectByteBufferPool byteBufferPool;
    private final ByteBuffer buffer;

    public TupleBuilder(DirectByteBufferPool byteBufferPool) {
        this.buffer = byteBufferPool.acquire();
        this.byteBufferPool = byteBufferPool;
    }

    /*
        format: [tokenSize][token][tokenSize][token]
                [4]/[4]foo[4]/[7]foobar
     */

    public TupleBuilder add(byte element) {
        buffer.putInt(Byte.SIZE);
        buffer.put((byte) PrimitiveType.BYTE.ordinal());
        buffer.put(element);
        return this;
    }

    public TupleBuilder add(Boolean element) {
        buffer.putInt(Byte.SIZE);
        buffer.put((byte) PrimitiveType.BOOLEAN.ordinal());
        buffer.put((byte) (Boolean.TRUE.equals(element) ? 1 : 0));
        return this;
    }

    public TupleBuilder add(String element) {
        byte[] bytes = element.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put((byte) PrimitiveType.STRING.ordinal());
        buffer.put(bytes);
        return this;
    }

    public TupleBuilder add(Short element) {
        buffer.putInt(Short.SIZE);
        buffer.put((byte) PrimitiveType.SHORT.ordinal());
        buffer.putShort(element);
        return this;
    }

    public TupleBuilder add(Integer element) {
        buffer.putInt(Integer.SIZE);
        buffer.put((byte) PrimitiveType.INTEGER.ordinal());
        buffer.putInt(element);
        return this;
    }

    public TupleBuilder add(Long element) {
        buffer.putInt(Long.SIZE);
        buffer.put((byte) PrimitiveType.LONG.ordinal());
        buffer.putLong(element);
        return this;
    }

    public TupleBuilder add(Float element) {
        buffer.putInt(Float.SIZE);
        buffer.put((byte) PrimitiveType.FLOAT.ordinal());
        buffer.putFloat(element);
        return this;
    }

    public TupleBuilder add(Double element) {
        buffer.putInt(Double.SIZE);
        buffer.put((byte) PrimitiveType.DOUBLE.ordinal());
        buffer.putDouble(element);
        return this;
    }

    public byte[] build() {
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        byteBufferPool.release(buffer);
        return bytes;
    }
}
