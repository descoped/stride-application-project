package io.descoped.stride.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.JsonElement;
import no.cantara.config.ApplicationProperties;
import org.glassfish.hk2.api.DynamicConfiguration;
import org.glassfish.hk2.api.DynamicConfigurationService;
import org.glassfish.hk2.api.Filter;
import org.glassfish.hk2.api.MultiException;
import org.glassfish.hk2.api.Populator;
import org.glassfish.hk2.api.PopulatorPostProcessor;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.api.ServiceLocatorFactory;
import org.glassfish.hk2.extras.events.internal.DefaultTopicDistributionService;
import org.glassfish.hk2.runlevel.RunLevelController;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.ClasspathDescriptorFileFinder;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
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
        log.debug("Config:\n{}", this.configuration.toPrettyString());
    }

    public ServiceLocator getServiceLocator() {
        return serviceLocator;
    }

    /*
    public String getHost() {
        String host = ((ServerConnector) jettyServerRef.get().getConnectors()[0]).getHost();
        return host == null ? "localhost" : host;
    }

    public int getPort() {
        int port = ((ServerConnector) jettyServerRef.get().getConnectors()[0]).getPort();
        return port;
    }

    public int getBoundPort() {
        int port = ((ServerConnector) jettyServerRef.get().getConnectors()[0]).getLocalPort();
        return port;
    }
    */

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
        if (log.isDebugEnabled()) {
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
//                    , new DuplicatePostProcessor()
                    , new ConfigurationPostPopulatorProcessor(configuration)
            );
        } catch (IOException e) {
            throw new MultiException(e);
        }
    }

    Filter getEnabledServicesFilter() {
        return descriptor -> {
            boolean defaultValue = configuration.asBoolean("hk2.defaults.enabled", false);
            boolean enabled = ofNullable(descriptor.getName())
                    .map(configuration::find)
                    .map(e -> e.with("enabled"))
                    .map(e -> e.asBoolean(defaultValue))
                    .orElse(false);
            if (descriptor.getName() != null) {
                log.debug("Service: {}.enabled={}", descriptor.getName(), enabled);
            }
            return enabled;
        };
    }

    // ---------------------------------------------------------------------------------------------------------------

    public record ConfigurationPostPopulatorProcessor(ApplicationConfiguration configuration)
            implements PopulatorPostProcessor {

        @Override
        public DescriptorImpl process(ServiceLocator serviceLocator, DescriptorImpl descriptorImpl) {
            String name = descriptorImpl.getName();
            if (name == null) {
                return descriptorImpl;
            }

            boolean defaultValue = configuration.asBoolean("hk2.defaults.enabled", false);

            JsonElement serviceConfiguration = configuration.find(name);
            boolean enabled = Optional.of(serviceConfiguration)
                    .map(e -> e.with("enabled"))
                    .map(e -> e.asBoolean(defaultValue))
                    .orElse(false);


            if (enabled) {
                serviceConfiguration.with("metadata").getObjectAs(object -> {
                    Map<String, List<String>> metadatas = new HashMap<>();
                    Function<JsonNode, List<String>> mapper = new Function<>() {
                        @Override
                        public List<String> apply(JsonNode json) {
                            if (json instanceof ArrayNode an) {
                                List<String> values = new ArrayList<>();
                                an.forEach(node -> values.addAll(apply(node)));
                                return values;
                            } else {
                                return List.of(json.asText());
                            }
                        }
                    };
                    JsonElement.toFlattenedMap(metadatas, "", object, mapper);
                    return metadatas;
                }).ifPresent(descriptorImpl::addMetadata);
                log.debug("Enabled {}", descriptorImpl.getImplementation() + "(" + name + ".enabled=true)");
                return descriptorImpl;
            } else {
                log.debug("Disabled {}", descriptorImpl.getImplementation() + "(" + name + ".enabled=false)");
                return null;
            }
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
