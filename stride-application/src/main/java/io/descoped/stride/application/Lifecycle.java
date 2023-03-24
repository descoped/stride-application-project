package io.descoped.stride.application;

import io.descoped.stride.application.config.ApplicationConfiguration;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevelController;

class Lifecycle {
    private final ApplicationConfiguration configuration;
    private final ServiceLocator serviceLocator;
    private final BeanDiscovery beanDiscovery;

    Lifecycle(ApplicationConfiguration configuration, ServiceLocator serviceLocator, BeanDiscovery beanDiscovery) {
        this.configuration = configuration;
        this.serviceLocator = serviceLocator;
        this.beanDiscovery = beanDiscovery;
    }

    void configure() {
        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        runLevelController.setThreadingPolicy(RunLevelController.ThreadingPolicy.valueOf(configuration.asString("hk2.threadpolicy", "FULLY_THREADED")));
        runLevelController.setMaximumUseableThreads(configuration.asInt("hk2.threadcount", 20));
    }

    void preStart() {
        beanDiscovery.populateServiceLocator();

        configure();

        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        if (runLevelController.getMaximumUseableThreads() > 3) {
            runLevelController.proceedTo(3); // start run levels before jetty server start
        }
    }

    void start() {
        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        runLevelController.proceedTo(runLevelController.getMaximumUseableThreads()); // start all run levels
    }

    void preStop() {
        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        runLevelController.proceedTo(0);
    }

    void stop() {
        serviceLocator.shutdown();
    }
}
