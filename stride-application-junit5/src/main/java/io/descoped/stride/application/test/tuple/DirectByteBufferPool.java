package io.descoped.stride.application.test.tuple;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class DirectByteBufferPool implements AutoCloseable {

    final BlockingQueue<ByteBuffer> deque;

    public DirectByteBufferPool(int n, int capacity) {
        deque = new LinkedBlockingDeque<>(n);
        for (int i = 0; i < n; i++) {
            try {
                deque.put(ByteBuffer.allocateDirect(capacity));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public ByteBuffer acquire() {
        try {
            int failCount = 25;
            ByteBuffer buffer;
            while ((buffer = deque.poll(1, TimeUnit.SECONDS)) == null) {
                if (failCount == 0) {
                    throw new RuntimeException("Unable to acquire KeyBuffer!");
                }
                TimeUnit.MILLISECONDS.sleep(50);
                failCount--;
            }
            return buffer;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void release(ByteBuffer buffer) {
        try {
            deque.put(buffer.clear());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        deque.clear();
    }
}
