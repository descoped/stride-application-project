package io.descoped.stride.application.core;

import io.descoped.stride.application.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationInitialization {

    private static final Logger log = LoggerFactory.getLogger(ApplicationInitialization.class);
    private final ApplicationConfiguration configuration;

    public ApplicationInitialization(ApplicationConfiguration configuration) {
        this.configuration = configuration;
    }

    public StrideApplication initialize() {
        BeanDiscovery beanDiscovery = new BeanDiscovery(configuration);
        DynamicConfiguration dc = beanDiscovery.getDynamicConfiguration();
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration));

        // register services
        for (io.descoped.stride.application.config.Service service : configuration.services().iterator()) {
            if (!service.isEnabled() || service.className() == null) {
                continue;
            }
            if (service.getClass().isAnnotationPresent(Service.class)) {
                continue;
            }
            DescriptorImpl descriptorImpl = BuilderHelper.link(service.clazz(), true)
                    .to(Service.class)
                    .in(RunLevel.class)
                    .build();
            descriptorImpl.addMetadata(RunLevel.RUNLEVEL_MODE_META_TAG, String.valueOf(RunLevel.RUNLEVEL_MODE_VALIDATING));
            descriptorImpl.addMetadata(RunLevel.RUNLEVEL_VAL_META_TAG, String.valueOf(service.runLevel()));
            log.trace("--> {}", descriptorImpl);
            dc.bind(descriptorImpl, false);
        }

        // register filters config
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration.filters()));

        // register servlets config
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration.servlets()));

        // register resources config
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration.resources()));

        // create application
        StrideApplication strideApplication = new StrideApplicationImpl(configuration, beanDiscovery);
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(strideApplication));
        return strideApplication;
    }
}
