package io.descoped.stride.application.core;

import io.descoped.stride.application.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.runlevel.RunLevelController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class StrideApplicationImpl implements StrideApplication {

    static {
        Logging.init();
    }

    private static final Logger log = LoggerFactory.getLogger(StrideApplication.class);
    private final ApplicationConfiguration configuration;
    private final ServiceLocator serviceLocator; // dynamic instance configuration
    private final Lifecycle lifecycle;

    public StrideApplicationImpl(ApplicationConfiguration configuration, BeanDiscovery beanDiscovery) {
        this.configuration = configuration;
        this.serviceLocator = ServiceLocatorUtils.instance();
//        DynamicConfiguration dynamicConfiguration = beanDiscovery.getDynamicConfiguration();
//        dynamicConfiguration.addActiveDescriptor(BuilderHelper.createConstantDescriptor(this));
//        dynamicConfiguration.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration));
        this.lifecycle = new Lifecycle(this.configuration, serviceLocator, beanDiscovery);
    }

    @Override
    public void activate() {
        proceedTo(lifecycle.getServiceRunLevel());
    }

    @Override
    public synchronized void proceedTo(int runLevel) {
        if (runLevel <= 0 && lifecycle.getCurrentRunLevel() > 0) {
            stop();

        } else if (runLevel == lifecycle.getMaxRunLevel()) {
            start();

        } else {
            lifecycle.proceedTo(runLevel);
        }
    }

    @Override
    public void start() {
        doStart();
    }

    @Override
    public void stop() {
        doStop();
    }

    private void doStart() {
        lifecycle.start();

        Filter configurationFilter = getEnabledServicesFilter();
        logConfiguredServices(configurationFilter);
    }

    private void doStop() {
        lifecycle.stop();
    }

    @Override
    public void close() {
        stop();
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public ServiceLocator getServiceLocator() {
        return serviceLocator;
    }

    public Optional<ServerConnector> getConnector() {
        Server server = serviceLocator.getService(Server.class);
        if (server == null) {
            int runLevel = ofNullable(serviceLocator.getService(RunLevelController.class))
                    .map(RunLevelController::getCurrentRunLevel)
                    .orElse(-1);
            log.error("Jetty Server is NOT started (run-level: {})", runLevel);
            return Optional.empty();
        }
        for (Connector connector : server.getConnectors()) {
            // the first connector should be the http connector
            ServerConnector serverConnector = (ServerConnector) connector;
            List<String> protocols = serverConnector.getProtocols();
            if (!protocols.contains("ssl") && (protocols.contains("http/1.1") || protocols.contains("h2c"))) {
                return Optional.of(serverConnector);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------------------------------------------------------

    Filter getEnabledServicesFilter() {
        return descriptor -> {
            boolean defaultValue = configuration.asBoolean("services.hk2.defaults.enabled", false);
            String name = descriptor.getName();
            boolean enabled = ofNullable(name)
                    .map(configuration.with("services")::find)
                    .map(e -> e.with("enabled"))
                    .map(e -> e.asBoolean(defaultValue))
                    .orElse(true);
            if (name != null && log.isDebugEnabled() && configuration.isVerboseLogging()) {
                log.debug("Service: {}.enabled={}", name, enabled);
            }
            return enabled;
        };
    }

    void logConfiguredServices(Filter configurationFilter) {
        List<ServiceHandle<?>> services = serviceLocator.getAllServiceHandles(configurationFilter);
        if (log.isDebugEnabled() && configuration.isVerboseLogging()) {
            StringBuilder msg = new StringBuilder();
            msg.append("Configured services:");
            for (ServiceHandle<?> service : services) {
                msg.append("\n\t|- ")
                        .append(service.getActiveDescriptor().getImplementation())
                        .append(" -> metadata: [")
                        .append(service.getActiveDescriptor().getMetadata()
                                .entrySet().stream().map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                                .collect(Collectors.joining(",")))
                        .append("]");
            }
            log.debug("{}", msg);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    static String elapsedTime(long startedAt) {
        Duration duration = Duration.ofMillis(System.currentTimeMillis() - startedAt);
        return String.format(
                "%d days, %d hours, %d minutes, %d seconds, %d millisecond",
                duration.toDays(),
                duration.toHours() % 24,
                duration.toMinutes() % 60,
                duration.toSeconds() % 60,
                duration.toMillis() % 1000);
    }

    // ---------------------------------------------------------------------------------------------------------------

    public static void main(String[] args) {
        long startedAt = System.currentTimeMillis();

        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .build();

        try (StrideApplication application = StrideApplication.create(configuration)) {

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                application.close();
                log.warn("Application shutdown after {}", elapsedTime(startedAt));
            }));

            application.start();

            log.info("Application started in {}ms", System.currentTimeMillis() - startedAt);

            try {
                Thread.currentThread().join();

                log.warn("Application shutdown after {}", elapsedTime(startedAt));
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
}
