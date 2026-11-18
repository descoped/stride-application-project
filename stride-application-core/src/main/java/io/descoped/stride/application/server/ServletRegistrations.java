package io.descoped.stride.application.server;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.config.ServletContextBinding;
import io.descoped.stride.application.config.ServletContextInitialization;
import io.descoped.stride.application.config.ServletContextValidation;
import io.descoped.stride.application.core.ServletContextInitializer;
import jakarta.servlet.Servlet;
import jakarta.validation.ValidationException;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Optional.ofNullable;

/**
 * Runs the servlet context initializers and registers the application's servlets. Like the filters, they belong
 * to the server, not to Jersey: they are there whether or not the Jersey service is enabled.
 */
final class ServletRegistrations {

    private static final Logger log = LoggerFactory.getLogger(ServletRegistrations.class);

    private final ServiceLocator serviceLocator;
    private final ServiceLocator webServiceLocator;
    private final ServletContextHandler servletContextHandler;

    /**
     * @param serviceLocator    the application's locator: creates initializers and servlets
     * @param webServiceLocator its child, handed to the initializers to put their services in
     */
    ServletRegistrations(ServiceLocator serviceLocator, ServiceLocator webServiceLocator,
                         ServletContextHandler servletContextHandler) {
        this.serviceLocator = serviceLocator;
        this.webServiceLocator = webServiceLocator;
        this.servletContextHandler = servletContextHandler;
    }

    void register(ApplicationConfiguration configuration, Components components) {
        runInitializers(configuration.initialization());
        registerServlets(configuration, components);
    }

    private void runInitializers(ServletContextInitialization initialization) {
        for (Class<?> initializerClass : initialization.classes()) {
            ServletContextInitializer initializer = (ServletContextInitializer) serviceLocator.createAndInitialize(initializerClass);
            initializer.initialize(webServiceLocator, servletContextHandler.getServletContext());
        }

        // validate requires, once every initializer has run: one may provide what another does not
        ServletContextValidation validation = initialization.validation();
        if (validation != null) {
            validate(validation, "the servlet context initialization");
        }
    }

    /**
     * Fails when something the configuration requires is not there: a named service, looked up where the
     * initializers put theirs, or a servlet context attribute.
     */
    private void validate(ServletContextValidation validation, String where) {
        for (ServletContextValidation.NamedService namedService : validation.serviceLocatorNamedServices()) {
            ServiceHandle<?> serviceHandle = webServiceLocator.getServiceHandle(namedService.type(), namedService.named());
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

    private void registerServlets(ApplicationConfiguration configuration, Components components) {
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
                validate(validation, "'" + servlet.name() + "' servlet");
            }

            ServletHolder servletHolder = new ServletHolder(servletInstance);
            servletSpecList.add(new ServletSpec(servletHolder, servlet.pathSpec()));
        }

        for (ServletSpec servletSpec : servletSpecList) {
            servletContextHandler.addServlet(servletSpec.holder, servletSpec.pathSpec());
        }
    }

    // An instance without any configuration entry is never met by the loop over the configured servlets, so its
    // missing path is caught here.
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

    record ServletSpec(ServletHolder holder, String pathSpec) {
    }
}
