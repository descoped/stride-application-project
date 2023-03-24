package io.descoped.stride.application;

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

class BeanDiscovery {
    private final ApplicationConfiguration configuration;
    private final ServiceLocator serviceLocator;

    BeanDiscovery(ApplicationConfiguration configuration, ServiceLocator serviceLocator) {
        this.configuration = configuration;
        this.serviceLocator = serviceLocator;
        populateServiceLocator();
    }

    void populateServiceLocator() throws MultiException {
        DynamicConfigurationService dcs = serviceLocator.getService(DynamicConfigurationService.class);

        DynamicConfiguration dynamicConfiguration = ServiceLocatorUtilities.createDynamicConfiguration(serviceLocator);
        dynamicConfiguration.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration));
        dynamicConfiguration.addActiveDescriptor(BuilderHelper.createConstantDescriptor(this));
        dynamicConfiguration.addActiveDescriptor(DefaultTopicDistributionService.class);
        dynamicConfiguration.commit();

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
