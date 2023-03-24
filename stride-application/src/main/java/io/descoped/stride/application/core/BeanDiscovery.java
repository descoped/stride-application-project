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

import java.io.IOException;
import java.util.Optional;

import static java.util.Optional.ofNullable;

class BeanDiscovery {
    private final InstanceFactory instanceFactory;
    private final ServiceLocator serviceLocator;

    BeanDiscovery(InstanceFactory instanceFactory, ServiceLocator serviceLocator) {
        this.instanceFactory = instanceFactory;
        this.serviceLocator = serviceLocator;
    }

    void populateServiceLocator() throws MultiException {
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
    }
}
