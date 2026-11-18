package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.server.RunLevelConstants;
import io.descoped.stride.application.server.VirtualThreads;
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
        if (!configured.get()) {
            RunLevelController runLevelController = ofNullable(serviceLocator.getService(RunLevelController.class)).orElseThrow(() -> new IllegalStateException("RunLevelController is yet not available!"));

            runLevelController.setThreadingPolicy(RunLevelController.ThreadingPolicy.valueOf(configuration.requireString("services.hk2.threadpolicy")));
            runLevelController.setMaximumUseableThreads(configuration.requireInt("services.hk2.threadcount"));
            // services start on virtual threads; threadcount bounds how many start at the same time
            runLevelController.setExecutor(VirtualThreads.perTaskExecutor("stride-runlevel-"));
            configured.set(true);
        }
    }

    boolean isRunning() {
        return configured.get() && !completed.get();
    }

    boolean isCompleted() {
        return completed.get();
    }

    synchronized int getCurrentRunLevel() {
        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        return runLevelController == null ? 0 : runLevelController.getCurrentRunLevel();
    }

    int getServiceRunLevel() {
        return RunLevelConstants.WEB_SERVER_RUN_LEVEL - 1;
    }

    int getMaxRunLevel() {
        beanDiscovery.discover();
        return serviceLocator.getDescriptors(d -> true).stream()
                .map(d -> d.getMetadata().get(org.glassfish.hk2.runlevel.RunLevel.RUNLEVEL_VAL_META_TAG))
                .filter(java.util.Objects::nonNull).flatMap(java.util.Collection::stream)
                .mapToInt(Integer::parseInt).max().orElse(RunLevelConstants.WEB_SERVER_RUN_LEVEL + 1);
    }

    synchronized void proceedTo(int runLevel) {
        if (completed.get()) {
            if (runLevel > 0) {
                ensureNotStopped();
            }
            return;
        }

        beanDiscovery.discover();

        configure();

        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        runLevelController.proceedTo(runLevel);

        completed.set(runLevel == 0);
    }

    // the service locator is shut down with the application; its services cannot come back
    void ensureNotStopped() {
        if (completed.get()) {
            throw new IllegalStateException("The application is stopped and cannot be started again; create a new one");
        }
    }

    void start() {
        ensureNotStopped();
        proceedTo(getMaxRunLevel()); // start all run levels
    }

    void stop() {
        if (serviceLocator.isShutdown()) return;
        try { proceedTo(0); }
        finally { serviceLocator.shutdown(); completed.set(true); }
    }
}
