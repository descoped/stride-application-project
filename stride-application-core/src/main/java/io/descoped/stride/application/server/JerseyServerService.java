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
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.runlevel.RunLevel;
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
import java.util.stream.Collectors;

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
        runInitializers(configuration.initialization(), serviceLocator, servletContextHandler);

        registerServlets(configuration, components, serviceLocator, servletContextHandler);

        registerResources(configuration, components, resourceConfig);

        // initialize container
        servletContainer = new JerseyServletContainer(resourceConfig);
        ServletHolder servletHolder = new ServletHolder(servletContainer);
        servletHolder.setInitOrder(1);
        servletContextHandler.addServlet(servletHolder, "/*");

        log.info("Jersey Servlet container started!");
    }

    private void runInitializers(ServletContextInitialization initialization,
                                 ServiceLocator serviceLocator,
                                 ServletContextHandler servletContextHandler) {
        for (Class<?> initializerClass : initialization.classes()) {
            ServletContextInitializer initializer = (ServletContextInitializer) serviceLocator.createAndInitialize(initializerClass);
            initializer.initialize(jerseyServiceLocator, servletContextHandler.getServletContext());
        }

        // validate requires, once every initializer has run: one may provide what another does not
        ServletContextValidation validation = initialization.validation();
        if (validation != null) {
            validate(validation, servletContextHandler, "the servlet context initialization");
        }
    }

    /**
     * Fails when something the configuration requires is not there: a named service, looked up where the
     * initializers put theirs, or a servlet context attribute.
     */
    private void validate(ServletContextValidation validation, ServletContextHandler servletContextHandler, String where) {
        for (ServletContextValidation.NamedService namedService : validation.serviceLocatorNamedServices()) {
            ServiceHandle<?> serviceHandle = jerseyServiceLocator.getServiceHandle(namedService.type(), namedService.named());
            if (serviceHandle == null) {
                throw new ValidationException(String.format("Required service '%s' named '%s' for %s is NOT available!",
                        namedService.type().getName(), namedService.named(), where));
            }
        }
        for (String attribute : validation.servletContextAttributes()) {
            if (servletContextHandler.getServletContext().getAttribute(attribute) == null) {
                throw new ValidationException(String.format("Required servletContext attribute '%s' for %s IS NOT set!",
                        attribute, where));
            }
        }
    }

    private void registerServlets(ApplicationConfiguration configuration,
                                         Components components,
                                         ServiceLocator serviceLocator,
                                         ServletContextHandler servletContextHandler) {
        // register servlets
        List<ServletSpec> servletSpecList = new ArrayList<>();

        Services services = configuration.services();
        requirePathForServletInstances(configuration, components);
        for (io.descoped.stride.application.config.Servlet servlet : configuration.servlets().iterator()) {
            Servlet servletInstance = components.servlets().get(servlet.name());
            if (servletInstance == null && !servlet.isEnabled()) continue;
            if (servlet.pathSpec() == null) {
                throw new IllegalStateException("Servlet '" + servlet.name() + "' has no path: set servlets."
                        + servlet.name() + ".config.pathSpec");
            }
            if (servletInstance != null && !servlet.isEnabled() && servlet.json().has("enabled")) {
                // code decides which components exist; an instance is never dropped by configuration
                log.warn("Servlet '{}' was handed in as an instance and is registered although servlets.{}.enabled is false",
                        servlet.name(), servlet.name());
            }
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

            ServletContextValidation validation = servlet.validation();
            if (validation != null) {
                validate(validation, servletContextHandler, "'" + servlet.name() + "' servlet");
            }

            ServletHolder servletHolder = new ServletHolder(servletInstance);
            servletSpecList.add(new ServletSpec(servletHolder, servlet.pathSpec()));
        }

        for (ServletSpec servletSpec : servletSpecList) {
            servletContextHandler.addServlet(servletSpec.holder, servletSpec.pathSpec());
        }
    }

    private static void requirePathForServletInstances(ApplicationConfiguration configuration, Components components) {
        Set<String> servletsWithPath = new HashSet<>();
        for (io.descoped.stride.application.config.Servlet servlet : configuration.servlets().iterator()) {
            if (servlet.pathSpec() != null) servletsWithPath.add(servlet.name());
        }
        for (String name : components.servlets().keySet()) {
            if (!servletsWithPath.contains(name)) {
                throw new IllegalStateException("Servlet '" + name + "' has no path: set servlets." + name + ".config.pathSpec");
            }
        }
    }

    private void registerResources(ApplicationConfiguration configuration,
                                   Components components,
                                   ResourceConfig resourceConfig) throws ClassNotFoundException {
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
                    resourceConfig.register(createResource(resourceClass, args));
                }

                log.debug("Registered: {}", resourceClass);

            } catch (ClassNotFoundException e) {
                log.error("Could not load resource: {}", resourceClass, e);
                throw e;
            }
        }
    }

    /**
     * Creates a resource whose configuration names its constructor arguments: each argument is a service, looked
     * up by type and name and passed to the constructor of exactly those types. The class needs no injection
     * annotations, which is the point: it is created the way the application would create it with {@code new}.
     */
    private Object createResource(String resourceClass, List<Arg> args) throws ClassNotFoundException {
        Class<?> resourceClazz = getClass().getClassLoader().loadClass(resourceClass);

        List<Class<?>> parameterClasses = new ArrayList<>();
        List<Object> parameterInstances = new ArrayList<>();
        for (Arg arg : args) {
            Object service = jerseyServiceLocator.getService(arg.clazz(), arg.named());
            if (service == null) {
                throw new IllegalStateException("Resource " + resourceClass + " needs a service of type "
                        + arg.clazz().getName() + " named '" + arg.named() + "', and there is none");
            }
            parameterClasses.add(arg.clazz());
            parameterInstances.add(service);
        }

        try {
            Constructor<?> constructor = resourceClazz.getDeclaredConstructor(parameterClasses.toArray(new Class[0]));
            return constructor.newInstance(parameterInstances.toArray(new Object[0]));

        } catch (InvocationTargetException e) {
            // the cause is in the message too: HK2 reports a failed start by its messages only
            throw new IllegalStateException("The constructor of resource " + resourceClass + " failed: " + e.getCause(),
                    e.getCause());
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Resource " + resourceClass + " could not be created with a constructor "
                    + "taking (" + parameterClasses.stream().map(Class::getName).collect(Collectors.joining(", "))
                    + ")", e);
        }
    }

    @Override
    public void preDestroy() {
        servletContainer.stop();
        if (!jerseyServiceLocator.isShutdown()) jerseyServiceLocator.shutdown();
    }

    record ServletSpec(ServletHolder holder, String pathSpec) {
    }
}
