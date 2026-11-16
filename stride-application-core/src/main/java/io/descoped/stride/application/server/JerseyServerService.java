package io.descoped.stride.application.server;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Arg;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.config.ServletContextBinding;
import io.descoped.stride.application.config.ServletContextInitialization;
import io.descoped.stride.application.config.ServletContextValidation;
import io.descoped.stride.application.core.ServletContextInitializer;
import io.descoped.stride.application.api.Components;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.servlet.DispatcherType;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.validation.ValidationException;
import org.eclipse.jetty.ee11.servlet.FilterHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.glassfish.hk2.api.Injectee;
import org.glassfish.hk2.api.JustInTimeInjectionResolver;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Optional.ofNullable;

@Service(name = "jersey.server")
@RunLevel(RunLevelConstants.WEB_SERVER_RUN_LEVEL)
public class JerseyServerService implements PreDestroy {

    private static final Logger log = LoggerFactory.getLogger(JerseyServerService.class);

    private final JerseyServletContainer servletContainer;
    private final ServiceLocator jerseyServiceLocator;

    @SuppressWarnings("JavacQuirks")
    @Inject
    public JerseyServerService(ApplicationConfiguration configuration,
                               Components components,
                               ServiceLocator serviceLocator,
                               ServletContextHandler servletContextHandler) throws ClassNotFoundException {

        ApplicationConfiguration.Jersey jerseyConfig = configuration.jersey();

        // create rest resource config
        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.property(ServerProperties.WADL_FEATURE_DISABLE, Boolean.TRUE);
        resourceConfig.register(RolesAllowedDynamicFeature.class);
        resourceConfig.property(ServerProperties.UNWRAP_COMPLETION_STAGE_IN_WRITER_ENABLE, Boolean.TRUE);

        // register supported mediaTypes
        String mediaTypesString = String.join(",", jerseyConfig.mediaTypes());
        resourceConfig.property(ServerProperties.MEDIA_TYPE_MAPPINGS, mediaTypesString);

        // initialize initializers
        jerseyServiceLocator = ServiceLocatorFactory.getInstance().create(null, serviceLocator);
        ServletContextInitialization initialization = configuration.initialization();
        for (Class<?> initializerClass : initialization.classes()) {
            ServletContextInitializer initializer = (ServletContextInitializer) serviceLocator.createAndInitialize(initializerClass);
            initializer.initialize(jerseyServiceLocator, servletContextHandler.getServletContext());

            // validate requires
            ServletContextValidation validation = initialization.validation();
            if (validation != null) {
                Set<ServletContextValidation.NamedService> namedServices = validation.serviceLocatorNamedServices();
                for (ServletContextValidation.NamedService namedService : namedServices) {
                    ServiceHandle<?> serviceHandle = jerseyServiceLocator.getServiceHandle(namedService.type(), namedService.named());
                    if (serviceHandle == null) {
                        throw new ValidationException(
                                String.format("Required service '%s' named '%s' NOT available!",
                                        namedService.type(),
                                        namedService.named()
                                )
                        );
                    }
                }

                Set<String> attributes = validation.servletContextAttributes();
                for (String attribute : attributes) {
                    boolean valid = servletContextHandler.getServletContext().getAttribute(attribute) != null;
                    if (!valid) {
                        throw new ValidationException(
                                String.format("Required servletContext attribute '%s' for '%s' servlet IS NOT set!",
                                        attribute,
                                        initializerClass
                                )
                        );
                    }
                }
            }
        }

        // register filters: one rule, ascending order then name
        for (FilterRegistration registration : filterRegistrations(configuration, components, serviceLocator)) {
            servletContextHandler.addFilter(new FilterHolder(registration.filter()), registration.pathSpec(), registration.dispatches());
            log.debug("Filter {} (order {}) on {}", registration.name(), registration.order(), registration.pathSpec());
        }

        // register servlets
        List<ServletSpec> servletSpecList = new ArrayList<>();

        Services services = configuration.services();
        Set<String> servletsWithPath = new HashSet<>();
        for (io.descoped.stride.application.config.Servlet servlet : configuration.servlets().iterator()) {
            if (servlet.pathSpec() != null) servletsWithPath.add(servlet.name());
        }
        for (String name : components.servlets().keySet()) {
            if (!servletsWithPath.contains(name)) {
                throw new IllegalStateException("Servlet '" + name + "' has no path: set servlets." + name + ".config.pathSpec");
            }
        }
        for (io.descoped.stride.application.config.Servlet servlet : configuration.servlets().iterator()) {
            Servlet servletInstance = components.servlets().get(servlet.name());
            if (!isEnabled(servlet.json(), servletInstance != null, servlet.isEnabled())) continue;
            if (servletInstance == null) {
                Class<? extends Servlet> servletClass = servlet.clazz();
                servletInstance = serviceLocator.createAndInitialize(servletClass);
            }

            // bind service to servlet binding
            ServletContextBinding binding = servlet.binding();
            if (binding != null) {
                binding.names().forEach(name -> ofNullable(binding.namedServiceByName(name))
                        .flatMap(services::service)
                        .map(io.descoped.stride.application.config.Service::clazz)
                        .map(serviceLocator::getService)
                        .ifPresent(instance -> servletContextHandler.getServletContext().setAttribute(name, instance)));
            }

            // validate servletContext attributes
            ServletContextValidation validation = servlet.validation();
            if (validation != null) {
                Set<String> requires = validation.servletContextAttributes();
                for (String require : requires) {
                    boolean valid = servletContextHandler.getServletContext().getAttribute(require) != null;
                    if (!valid) {
                        throw new ValidationException(
                                String.format("Required servletContext attribute '%s' for '%s' servlet IS NOT set!",
                                        require,
                                        servlet.name()
                                )
                        );
                    }
                }
            }

            ServletHolder servletHolder = new ServletHolder(servletInstance);
            servletSpecList.add(new ServletSpec(servletHolder, servlet.pathSpec()));
        }

        for (ServletSpec servletSpec : servletSpecList) {
            servletContextHandler.addServlet(servletSpec.holder, servletSpec.pathSpec());
        }

        // register resources: instances the application wired itself, then classes named in the configuration
        components.resources().forEach(resourceConfig::register);
        for (Resource resource : configuration.resources().iterator()) {
            if (!resource.isEnabled()) continue;
            String resourceClass = resource.className();
            try {
                List<Arg> args = resource.args();
                if (args.isEmpty()) {
                    resourceConfig.register(getClass().getClassLoader().loadClass(resourceClass));
                } else {
                    // create resource using reflection (TODO: replace this with HK2 Injection)
                    Class<?> resourceClazz = getClass().getClassLoader().loadClass(resourceClass);

                    List<Class<?>> parameterClasses = new ArrayList<>();
                    List<Object> parameterInstances = new ArrayList<>();
                    for (Arg arg : args) {
                        parameterClasses.add(arg.clazz());
                        parameterInstances.add(jerseyServiceLocator.getService(arg.clazz(), arg.named()));
                    }

                    try {
                        Constructor<?> constructor = resourceClazz.getDeclaredConstructor(parameterClasses.toArray(new Class[0]));
                        Object resourceInstance = constructor.newInstance(parameterInstances.toArray(new Object[0]));
                        resourceConfig.register(resourceInstance);

                    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                             InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }

                log.debug("Registered: {}", resourceClass);

            } catch (ClassNotFoundException e) {
                log.error("Could not load resource: {}", resourceClass, e);
                throw e;
            }
        }

        // initialize container
        servletContainer = new JerseyServletContainer(resourceConfig);
        ServletHolder servletHolder = new ServletHolder(servletContainer);
        servletHolder.setInitOrder(1);
        servletContextHandler.addServlet(servletHolder, "/*");

        log.info("Jersey Servlet container started!");
    }

    @Override
    public void preDestroy() {
        servletContainer.stop();
        if (!jerseyServiceLocator.isShutdown()) jerseyServiceLocator.shutdown();
    }

    record ServletSpec(ServletHolder holder, String pathSpec) {
    }

    record FilterRegistration(String name, int order, Filter filter, String pathSpec, EnumSet<DispatcherType> dispatches) {
    }

    /**
     * Collects every filter: those in the configuration, created from their class unless the application handed in
     * an instance of that name; and instances without a configuration entry.
     */
    static List<FilterRegistration> filterRegistrations(ApplicationConfiguration configuration, Components components,
                                                        ServiceLocator serviceLocator) {
        List<FilterRegistration> registrations = new ArrayList<>();
        Set<String> configured = new HashSet<>();
        for (io.descoped.stride.application.config.Filter filter : configuration.filters().iterator()) {
            configured.add(filter.name());
            Filter instance = components.filters().get(filter.name());
            if (!isEnabled(filter.json(), instance != null, filter.isEnabled())) continue;
            if (instance == null) {
                if (filter.className() == null) {
                    throw new IllegalStateException("Filter '" + filter.name() + "' is enabled but has neither "
                            + "filters." + filter.name() + ".config.class nor an instance in Components");
                }
                Class<? extends Filter> filterClass = filter.clazz();
                instance = serviceLocator.createAndInitialize(filterClass);
            }
            registrations.add(new FilterRegistration(filter.name(), filter.order(), instance, filter.pathSpec(), filter.dispatches()));
        }
        components.filters().forEach((name, instance) -> {
            if (!configured.contains(name)) {
                registrations.add(new FilterRegistration(name, io.descoped.stride.application.config.Filter.DEFAULT_ORDER, instance,
                        io.descoped.stride.application.config.Filter.DEFAULT_PATH_SPEC, EnumSet.of(DispatcherType.REQUEST)));
            }
        });

        registrations.sort(Comparator.comparingInt(FilterRegistration::order).thenComparing(FilterRegistration::name));
        return registrations;
    }

    // A component the application handed in as an instance is enabled unless the configuration disables it.
    private static boolean isEnabled(JsonNode json, boolean hasInstance, boolean configuredEnabled) {
        return hasInstance && !json.has("enabled") || configuredEnabled;
    }

    //    @Provider
    static class ResourceInjectionBinder extends AbstractBinder {

        private final Class<?> resourceClazz;

        public ResourceInjectionBinder(Class<?> resourceClazz) {
            this.resourceClazz = resourceClazz;
        }

        @Override
        protected void configure() {
            bind(resourceClazz).to(ResourceJustInTimeInjectionResolver.class);
        }
    }

    //    @Service
    static class ResourceJustInTimeInjectionResolver implements JustInTimeInjectionResolver {

        @Override
        public boolean justInTimeResolution(Injectee injectee) {
            if (injectee.getInjecteeClass() == null) {
                return false;
            }

            log.trace("{}", injectee.getRequiredType());

            System.err.println("-----: " + injectee.getInjecteeDescriptor().getInjectees());

            return false;
        }
    }
}
