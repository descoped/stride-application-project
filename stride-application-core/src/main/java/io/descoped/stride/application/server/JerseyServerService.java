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
import jakarta.inject.Inject;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import jakarta.servlet.Servlet;
import jakarta.validation.ValidationException;
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
        }

        // validate requires, once every initializer has run: one may provide what another does not
        ServletContextValidation initializationValidation = initialization.validation();
        if (initializationValidation != null) {
            for (ServletContextValidation.NamedService namedService : initializationValidation.serviceLocatorNamedServices()) {
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

            for (String attribute : initializationValidation.servletContextAttributes()) {
                boolean valid = servletContextHandler.getServletContext().getAttribute(attribute) != null;
                if (!valid) {
                    throw new ValidationException(
                            String.format("Required servletContext attribute '%s' for the servlet context initialization IS NOT set!",
                                    attribute
                            )
                    );
                }
            }
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
            if (servletInstance == null && !servlet.isEnabled()) continue;
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
