package io.descoped.stride.application.server;

import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JettyConnectorThreadPool extends QueuedThreadPool {

    private static final Logger log = LoggerFactory.getLogger(JettyConnectorThreadPool.class);

    @Override
    public Thread newThread(Runnable runnable) {
        var thread = super.newThread(runnable);
        thread.setUncaughtExceptionHandler(new LoggingUncaughtExceptionHandler());
        return thread;
    }

    public static final class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            log.warn("Unhandled exception detected on thread " + t.getName(), e);
        }
    }
}
