package io.descoped.stride.application.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * Stride runs application code on virtual threads: one new thread per request and per starting service, never a
 * pooled one. Code may block freely, and since Java 24 a {@code synchronized} block no longer pins the carrier
 * thread. Do not keep per-request state in a {@code ThreadLocal} expecting reuse; use a {@code ScopedValue}.
 */
public final class VirtualThreads {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreads.class);

    private VirtualThreads() {
    }

    /**
     * An executor that starts a new named virtual thread for every task and logs what a task fails to catch.
     *
     * @param namePrefix thread names are the prefix followed by a counter
     */
    public static Executor perTaskExecutor(String namePrefix) {
        ThreadFactory factory = Thread.ofVirtual()
                .name(namePrefix, 0)
                .uncaughtExceptionHandler((thread, e) -> log.warn("Unhandled exception on thread {}", thread.getName(), e))
                .factory();
        return task -> factory.newThread(task).start();
    }
}
