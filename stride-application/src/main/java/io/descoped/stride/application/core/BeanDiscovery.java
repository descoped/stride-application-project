package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.Populator;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.extras.events.internal.DefaultTopicDistributionService;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.ClasspathDescriptorFileFinder;
import org.glassfish.hk2.utilities.DuplicatePostProcessor;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Optional.ofNullable;

class BeanDiscovery {

    private static final Logger log = LoggerFactory.getLogger(BeanDiscovery.class);

    private final InstanceFactory instanceFactory;
    private final ServiceLocator serviceLocator;
    private final AtomicBoolean completed = new AtomicBoolean();

    BeanDiscovery(InstanceFactory instanceFactory, ServiceLocator serviceLocator) {
        this.instanceFactory = instanceFactory;
        this.serviceLocator = serviceLocator;
    }

    void discover() throws MultiException {
        if (completed.compareAndSet(false, true)) {
            populateServiceLocator();
        }
    }

    private void populateServiceLocator() throws MultiException {
        long past = System.currentTimeMillis();
        DynamicConfigurationService dcs = serviceLocator.getService(DynamicConfigurationService.class);
        DynamicConfiguration dynamicConfiguration = ServiceLocatorUtilities.createDynamicConfiguration(serviceLocator);
        for (Object instance : instanceFactory.instances()) {
            dynamicConfiguration.addActiveDescriptor(BuilderHelper.createConstantDescriptor(instance));
        }
        dynamicConfiguration.addActiveDescriptor(DefaultTopicDistributionService.class);
        dynamicConfiguration.commit();

        Populator populator = dcs.getPopulator();

        try {
            Optional<ApplicationConfiguration> configuration = ofNullable(instanceFactory.getOrNull(ApplicationConfiguration.class));
            populator.populate(
                    new ClasspathDescriptorFileFinder()
                    , new DuplicatePostProcessor()
                    , new DefaultConfigurationPostPopulatorProcessor(configuration.orElseThrow(() -> new IllegalStateException("Missing configuration!")))
            );
        } catch (IOException e) {
            throw new MultiException(e);
        }
        log.trace("Discovery completed in {}ms", System.currentTimeMillis() - past);

    }
}
