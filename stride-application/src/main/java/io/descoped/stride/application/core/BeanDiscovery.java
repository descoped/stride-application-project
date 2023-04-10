package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.Populator;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.extras.events.internal.DefaultTopicDistributionService;
import org.glassfish.hk2.utilities.ClasspathDescriptorFileFinder;
import org.glassfish.hk2.utilities.DuplicatePostProcessor;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

class BeanDiscovery {

    private static final Logger log = LoggerFactory.getLogger(BeanDiscovery.class);

    private final ApplicationConfiguration configuration;
    private final ServiceLocator serviceLocator;
    private final AtomicBoolean committed = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final DynamicConfiguration dynamicConfiguration;

    BeanDiscovery(ApplicationConfiguration configuration, ServiceLocator serviceLocator) {
        this.configuration = configuration;
        this.serviceLocator = serviceLocator;
        dynamicConfiguration = ServiceLocatorUtilities.createDynamicConfiguration(serviceLocator);
    }

    void discover() throws MultiException {
        if (completed.compareAndSet(false, true)) {
            propagate();
            if (committed.compareAndSet(false, true)) {
                dynamicConfiguration.commit();
                populate();
            }
        }
    }

    DynamicConfiguration getDynamicConfiguration() {
        if (committed.get()) {
            throw new IllegalStateException("The DynamicConfiguration is committed!");
        }
        return dynamicConfiguration;
    }

    private void propagate() throws MultiException {
        dynamicConfiguration.addActiveDescriptor(DefaultTopicDistributionService.class);
    }

    private void populate() {
        DynamicConfigurationService dcs = serviceLocator.getService(DynamicConfigurationService.class);
        Populator populator = dcs.getPopulator();

        try {
            populator.populate(
                    new ClasspathDescriptorFileFinder()
                    , new DuplicatePostProcessor()
                    , new DefaultConfigurationPostPopulatorProcessor(configuration)
            );
        } catch (IOException e) {
            throw new MultiException(e);
        }
    }
}
