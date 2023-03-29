package io.descoped.stride.application.core;

import io.descoped.stride.application.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Filters;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.config.Servlets;
import no.cantara.config.ApplicationProperties;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplicationBuilder implements StrideApplication.Builder {

    private static final Logger log = LoggerFactory.getLogger(ApplicationBuilder.class);

    private ApplicationProperties applicationProperties;
    private Services.Builder servicesBuilder;
    private Filters.Builder filtersBuilder;
    private Servlets.Builder servletsBuilder;

    public ApplicationBuilder() {
    }

    @Override
    public StrideApplication.Builder configuration(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
        return this;
    }

    @Override
    public StrideApplication.Builder services(Services.Builder servicesBuilder) {
        this.servicesBuilder = servicesBuilder;
        return this;
    }

    @Override
    public StrideApplication.Builder filters(Filters.Builder filtersBuilder) {
        this.filtersBuilder = filtersBuilder;
        return this;
    }

    @Override
    public StrideApplication.Builder servlets(Servlets.Builder servletsBuilder) {
        this.servletsBuilder = servletsBuilder;
        return this;
    }

    @Override
    public StrideApplication build() {
        if (applicationProperties == null) {
            applicationProperties = ApplicationProperties.builder()
                    .classpathPropertiesFile("application-defaults.properties")
                    .testDefaults()
                    .build();
        }

        ApplicationConfiguration configuration = new ApplicationConfiguration(applicationProperties);

        BeanDiscovery beanDiscovery = new BeanDiscovery(configuration);
        DynamicConfiguration dc = beanDiscovery.getDynamicConfiguration();
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration));
        Services services = servicesBuilder.build();

        // register services
        for (Services.Service service : services.list()) {
            DescriptorImpl descriptorImpl = BuilderHelper.link(service.clazz(), true)
                    .to(Service.class)
                    .in(RunLevel.class)
                    .build();
            descriptorImpl.addMetadata(RunLevel.RUNLEVEL_MODE_META_TAG, String.valueOf(RunLevel.RUNLEVEL_MODE_VALIDATING));
            descriptorImpl.addMetadata(RunLevel.RUNLEVEL_VAL_META_TAG, String.valueOf(service.runLevel()));
            //log.trace("--> {}", descriptorImpl);
            dc.bind(descriptorImpl, false);
        }

        return new StrideApplicationImpl(configuration, beanDiscovery);
    }
}
