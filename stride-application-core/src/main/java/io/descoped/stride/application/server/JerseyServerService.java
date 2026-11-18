package io.descoped.stride.application.server;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Arg;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.api.Components;
import jakarta.inject.Inject;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

        // where the servlet context initializers put their services: resource arguments are looked up there
        jerseyServiceLocator = Objects.requireNonNull(
                (ServiceLocator) servletContextHandler.getAttribute(JettyServerService.WEB_SERVICE_LOCATOR),
                "the server service did not set " + JettyServerService.WEB_SERVICE_LOCATOR);

        registerResources(configuration, components, resourceConfig);

        // initialize container
        servletContainer = new JerseyServletContainer(resourceConfig);
        ServletHolder servletHolder = new ServletHolder(servletContainer);
        servletHolder.setInitOrder(1);
        servletContextHandler.addServlet(servletHolder, "/*");

        log.info("Jersey Servlet container started!");
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
    }
}
