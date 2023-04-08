package io.descoped.stride.application.core;

import io.descoped.stride.application.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Deployment;
import no.cantara.config.ApplicationProperties;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.jvnet.hk2.annotations.Service;

public class ApplicationInitialization {

    private final Deployment deployment;

    public ApplicationInitialization(Deployment deployment) {
        this.deployment = deployment;
    }

    public StrideApplication initialize() {
        ApplicationProperties applicationProperties = deployment.properties();
        if (deployment.properties() == null) {
            // TODO do not read test config. Test config used be passed form Test Extension. Otherwise use default prod config
            applicationProperties = ApplicationProperties.builder()
                    .classpathPropertiesFile("application-defaults.properties")
                    .testDefaults()
                    .build();
        }

        ApplicationConfiguration configuration = new ApplicationConfiguration(applicationProperties);

        BeanDiscovery beanDiscovery = new BeanDiscovery(configuration);
        DynamicConfiguration dc = beanDiscovery.getDynamicConfiguration();
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration));

        // register services
        for (io.descoped.stride.application.config.Service service : deployment.services().iterator()) {
            DescriptorImpl descriptorImpl = BuilderHelper.link(service.clazz(), true)
                    .to(Service.class)
                    .in(RunLevel.class)
                    .build();
            descriptorImpl.addMetadata(RunLevel.RUNLEVEL_MODE_META_TAG, String.valueOf(RunLevel.RUNLEVEL_MODE_VALIDATING));
            descriptorImpl.addMetadata(RunLevel.RUNLEVEL_VAL_META_TAG, String.valueOf(service.runLevel()));
            //log.trace("--> {}", descriptorImpl);
            dc.bind(descriptorImpl, false);
        }

        // register filters config
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(deployment.filters()));

        // register servlets config
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(deployment.servlets()));

        // register resources config
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(deployment.resources()));

        // create application
        StrideApplication strideApplication = new StrideApplicationImpl(configuration, beanDiscovery);
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(strideApplication));
        return strideApplication;
    }
}
