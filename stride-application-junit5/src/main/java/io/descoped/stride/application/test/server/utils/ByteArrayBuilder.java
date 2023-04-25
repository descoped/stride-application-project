package io.descoped.stride.application.test.server.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ByteArrayBuilder {

    private static final Logger log = LoggerFactory.getLogger(ByteArrayBuilder.class);
    private static final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    private final DirectByteBufferPool byteBufferPool;
    private final ByteBuffer buffer;

    public static List<Object> toElements(DirectByteBufferPool byteBufferPool, byte[] representation) {
        ByteBuffer byteBuffer = byteBufferPool.acquire();
        List<Object> elements = new ArrayList<>();
        try {
            byteBuffer.put(representation, 0, representation.length);
            byteBuffer.flip();

            int offset;

            while (byteBuffer.position() < byteBuffer.limit()) {
                offset = byteBuffer.getInt();

                ByteBuffer bb = ByteBuffer.allocateDirect(offset);
                int numberOfBytesToRead = offset;
                do {
                    bb.put(byteBuffer.get());
                } while (--numberOfBytesToRead > 0);
                bb.flip();

                CharBuffer cb = CharBuffer.allocate((offset * 2) + 1);
                decoder.reset();
                CoderResult cr = decoder.decode(bb, cb, true);
                if (cr.isError()) {
                    cr.throwException();
                }
                cb.flip();

                elements.add(cb.toString());
            }
            return elements;

        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        } finally {
            byteBufferPool.release(byteBuffer);
        }
    }

    public ByteArrayBuilder(DirectByteBufferPool byteBufferPool) {
        this.buffer = byteBufferPool.acquire();
        this.byteBufferPool = byteBufferPool;
    }

    /*
        format: [tokenSize][token][tokenSize][token]
                [4]/[4]foo[4]/[7]foobar
     */

    public ByteArrayBuilder add(String element) {
        byte[] bytes = element.getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
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
