package io.descoped.stride.application.core;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.jvnet.hk2.annotations.Service;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

public class ApplicationInitialization {

    private final ApplicationConfiguration configuration;
    private final Components components;
    private final ServiceLocator serviceLocator;

    public ApplicationInitialization(ApplicationConfiguration configuration, Components components, ServiceLocator serviceLocator) {
        this.configuration = configuration;
        this.components = components;
        this.serviceLocator = serviceLocator;
    }

    public StrideApplication initialize() {
        BeanDiscovery beanDiscovery = new BeanDiscovery(configuration, serviceLocator);
        DynamicConfiguration dc = beanDiscovery.getDynamicConfiguration();

        // register configuration
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration));

        // register what the application wired itself: instances are bound as they are, never created or injected
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(components));
        for (Components.ServiceInstance<?> service : components.services()) {
            dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(service.instance(), service.name(), service.contract()));
        }

        // register services
        for (io.descoped.stride.application.config.Service service : configuration.services().iterator()) {
            // ignore disabled services and services without service class
            if (service.isEnabled() && service.className() != null) {
                dc.bind(runLevelDescriptor(service), false);
            }
        }

        // create application
        StrideApplication strideApplication = new StrideApplicationImpl(configuration, serviceLocator, beanDiscovery);
        dc.addActiveDescriptor(BuilderHelper.createConstantDescriptor(strideApplication));
        return strideApplication;
    }

    /** Describes a service class named in the configuration, to be created by Stride at its run level. */
    private static DescriptorImpl runLevelDescriptor(io.descoped.stride.application.config.Service service) {
        // Stride creates a configured class in the run level scope. A class that declares its own scope, or
        // that HK2 discovers by itself, cannot also be bound that way: say so instead of leaving it out.
        Class<?> serviceClass = service.clazz();
        if (serviceClass.isAnnotationPresent(Service.class) || Stream.of(serviceClass.getAnnotations())
                .anyMatch(annotation -> annotation.annotationType().isAnnotationPresent(Scope.class))) {
            throw new IllegalStateException("Service '" + service.name() + "' names " + serviceClass.getName()
                    + ", which declares its own scope or is an HK2 @Service. A class named in services."
                    + service.name() + ".config.class is created by Stride at its run level: remove the annotation, "
                    + "or remove config.class and let HK2 discover the service");
        }
        var link = BuilderHelper.link(serviceClass, true)
                .to(Service.class)
                .in(RunLevel.class);
        Named named = serviceClass.getAnnotation(Named.class);
        if (named != null) {
            link = link.named(named.value());
        }
        // The class keeps its other qualifiers too, as HK2's own discovery would give it: HK2 finds, for
        // example, the subscribers of topic events by their @MessageReceiver qualifier.
        for (Annotation annotation : serviceClass.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (type != Named.class && type.isAnnotationPresent(Qualifier.class)) {
                link = link.qualifiedBy(type.getName());
            }
        }
        DescriptorImpl descriptorImpl = link.build();
        descriptorImpl.addMetadata(RunLevel.RUNLEVEL_MODE_META_TAG, String.valueOf(RunLevel.RUNLEVEL_MODE_VALIDATING));
        descriptorImpl.addMetadata(RunLevel.RUNLEVEL_VAL_META_TAG, String.valueOf(service.runLevel()));
        return descriptorImpl;
    }
}
