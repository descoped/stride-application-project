package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.server.RunLevelConstants;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevelController;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Optional.ofNullable;

class Lifecycle {
    private final ApplicationConfiguration configuration;
    private final ServiceLocator serviceLocator;
    private final BeanDiscovery beanDiscovery;
    private final AtomicBoolean configured = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();

    Lifecycle(ApplicationConfiguration configuration, ServiceLocator serviceLocator, BeanDiscovery beanDiscovery) {
        this.configuration = configuration;
        this.serviceLocator = serviceLocator;
        this.beanDiscovery = beanDiscovery;
    }

    void configure() {
        if (configured.compareAndSet(false, true)) {
            RunLevelController runLevelController = ofNullable(serviceLocator.getService(RunLevelController.class)).orElseThrow(() -> new IllegalStateException("RunLevelController is yet not available!"));

            runLevelController.setThreadingPolicy(RunLevelController.ThreadingPolicy.valueOf(configuration.asString("hk2.threadpolicy", RunLevelController.ThreadingPolicy.FULLY_THREADED.name())));
            runLevelController.setMaximumUseableThreads(configuration.asInt("hk2.threadcount", 20));
        }
    }

    boolean isRunning() {
        return !completed.get();
    }

    synchronized int getCurrentRunLevel() {
        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        return runLevelController.getCurrentRunLevel();
    }

    int getServiceRunLevel() {
        return RunLevelConstants.WEB_SERVER_RUN_LEVEL - 1;
    }

    int getMaxRunLevel() {
        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        if (runLevelController == null) {
            return configuration.asInt("hk2.threadcount", 20);
        }
        return runLevelController.getMaximumUseableThreads();
    }

    synchronized void proceedTo(int runLevel) {
        if (completed.get()) {
            return;
        }

        beanDiscovery.discover();

        configure();

        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        if (runLevelController.getMaximumUseableThreads() >= runLevel) {
            runLevelController.proceedTo(runLevel);
        }

        completed.set(runLevel == 0);
    }

    void start() {
        proceedTo(getMaxRunLevel()); // start all run levels
    }

    void stop() {
        proceedTo(0); // stop all run levels
        if (!serviceLocator.isShutdown()) {
            serviceLocator.shutdown();
        }
    }
}
