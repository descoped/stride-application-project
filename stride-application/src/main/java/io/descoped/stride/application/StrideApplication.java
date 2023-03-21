package io.descoped.stride.application;

import io.descoped.stride.application.config.ApplicationConfiguration;
import no.cantara.config.ApplicationProperties;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.Populator;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.extras.events.internal.DefaultTopicDistributionService;
import org.glassfish.hk2.runlevel.RunLevelController;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.ClasspathDescriptorFileFinder;
import org.glassfish.hk2.utilities.DuplicatePostProcessor;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

public class StrideApplication implements AutoCloseable {

    static {
        Logging.init();
    }

    private static final Logger log = LoggerFactory.getLogger(StrideApplication.class);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ApplicationConfiguration configuration;
    private final ServiceLocator serviceLocator;

    public StrideApplication(ApplicationProperties configuration) {
        this.configuration = new ApplicationConfiguration(configuration);
        this.serviceLocator = ServiceLocatorFactory.getInstance().create(null);
        //log.debug("Config:\n{}", this.configuration.toPrettyString());
    }

    public ServiceLocator getServiceLocator() {
        return serviceLocator;
    }

    public int geHost() {
        int port = -1;
        Server server = serviceLocator.getService(Server.class);
        for (Connector connector : server.getConnectors()) {
            // the first connector should be the http connector
            ServerConnector serverConnector = (ServerConnector) connector;
            List<String> protocols = serverConnector.getProtocols();
            if (!protocols.contains("ssl") && (protocols.contains("http/1.1") || protocols.contains("h2c"))) {
                port = serverConnector.getLocalPort();
                break;
            }
        }
        return port;
    }

    private Optional<ServerConnector> getConnector() {
        Server server = serviceLocator.getService(Server.class);
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

    public String getHost() {
        Optional<ServerConnector> connector = getConnector();
        return connector.map(ServerConnector::getHost).orElse("localhost");
    }

    public int getLocalPort() {
        Optional<ServerConnector> connector = getConnector();
        return connector.map(ServerConnector::getLocalPort).orElse(-1);
    }

    public int getPort() {
        Optional<ServerConnector> connector = getConnector();
        return connector.map(ServerConnector::getPort).orElse(-1);
    }

    public void start() {
        if (closed.compareAndSet(false, true)) {
            doStart();
        }
    }

    public void stop() {
        if (closed.compareAndSet(true, false)) {
            doStop();
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void doStart() {
        populateServiceLocator(serviceLocator);

        Filter configurationFilter = getEnabledServicesFilter();

        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        runLevelController.setThreadingPolicy(RunLevelController.ThreadingPolicy.valueOf(configuration.asString("hk2.threadpolicy", "FULLY_THREADED")));
        runLevelController.setMaximumUseableThreads(configuration.asInt("hk2.threadcount", 5));
        runLevelController.proceedTo(configuration.asInt("hk2.runlevel", 20));

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

    private void doStop() {
        RunLevelController runLevelController = serviceLocator.getService(RunLevelController.class);
        runLevelController.proceedTo(0);
        serviceLocator.shutdown();
    }

    private void populateServiceLocator(ServiceLocator serviceLocator) throws MultiException {
        DynamicConfigurationService dcs = serviceLocator.getService(DynamicConfigurationService.class);

        DynamicConfiguration dynamicConfiguration = ServiceLocatorUtilities.createDynamicConfiguration(serviceLocator);
        dynamicConfiguration.addActiveDescriptor(BuilderHelper.createConstantDescriptor(configuration));
        dynamicConfiguration.addActiveDescriptor(BuilderHelper.createConstantDescriptor(this));
        dynamicConfiguration.addActiveDescriptor(DefaultTopicDistributionService.class);
        dynamicConfiguration.commit();

        Populator populator = dcs.getPopulator();

        try {
            populator.populate(
                    new ClasspathDescriptorFileFinder()
                    , new DuplicatePostProcessor()
                    , new DefaultConfigurationPostPopulatorProcessor(configuration)
            );
        } catch (IOException e) {
            throw new MultiException(e);
        }
    }

    Filter getEnabledServicesFilter() {
        return descriptor -> {
            boolean defaultValue = configuration.asBoolean("hk2.defaults.enabled", false);
            String name = descriptor.getName();
            boolean enabled = ofNullable(name)
                    .map(configuration::find)
                    .map(e -> e.with("enabled"))
                    .map(e -> e.asBoolean(defaultValue))
                    .orElse(true);
            if (name != null && log.isDebugEnabled() && configuration.isVerboseLogging()) {
                log.debug("Service: {}.enabled={}", name, enabled);
            }
            return enabled;
        };
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

        ApplicationProperties properties = ApplicationProperties.builder()
                .classpathPropertiesFile("application-defaults.properties")
                .defaults()
                .enableEnvironmentVariables()
                .enableSystemProperties()
                .build();

        try (StrideApplication application = new StrideApplication(properties)) {

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
