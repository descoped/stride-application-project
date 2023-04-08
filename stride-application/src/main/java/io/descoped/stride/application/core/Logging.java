package io.descoped.stride.application.core;

import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.logging.LogManager;

public class Logging {

    static {
        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    public static void init() {
        // ignore
    }

}
