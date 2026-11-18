package io.descoped.stride.application.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.jackson.JsonElement;
import org.glassfish.hk2.api.PopulatorPostProcessor;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.DescriptorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

record DefaultConfigurationPostPopulatorProcessor(ApplicationConfiguration configuration)
        implements PopulatorPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DefaultConfigurationPostPopulatorProcessor.class);

    /**
     * The configuration of the service an HK2 descriptor names: {@code services.<name>}, or for the services HK2
     * names itself, {@code services.hk2.<name>}. The name is the whole path; a key of the same name elsewhere in
     * the configuration is not the service's.
     */
    static JsonElement serviceConfiguration(ApplicationConfiguration configuration, String name) {
        JsonElement services = configuration.with("services");
        JsonElement own = services.with(name);
        return own.with("enabled").asBoolean().isPresent() ? own : pick(own, services.with("hk2").with(name));
    }

    private static JsonElement pick(JsonElement own, JsonElement hk2) {
        return hk2.with("enabled").asBoolean().isPresent() ? hk2 : own;
    }

    /**
     * Whether a named service is switched on: {@code services.<name>.enabled} when the key is set, and
     * {@code services.hk2.defaults.enabled} when it is not.
     */
    static boolean isEnabled(ApplicationConfiguration configuration, String name) {
        return serviceConfiguration(configuration, name).with("enabled")
                .asBoolean(configuration.requireBoolean("services.hk2.defaults.enabled"));
    }

    @Override
    public DescriptorImpl process(ServiceLocator serviceLocator, DescriptorImpl descriptorImpl) {
        String name = descriptorImpl.getName();
        if (name == null) {
            logServiceStatus(descriptorImpl.getImplementation(), null, true);
            return descriptorImpl;
        }

        JsonElement serviceConfiguration = serviceConfiguration(configuration, name);
        boolean enabled = isEnabled(configuration, name);

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
            logServiceStatus(descriptorImpl.getImplementation(), name, true);
            return descriptorImpl;
        } else {
            logServiceStatus(descriptorImpl.getImplementation(), name, false);
            return null;
        }
    }

    private void logServiceStatus(String implementation, String name, boolean enabled) {
        if (log.isDebugEnabled() && configuration.isVerboseLogging()) {
            log.debug("{} {}[{}]",
                    enabled ? "Enabled" : "Disabled",
                    implementation,
                    name == null ? "" : "\"" + name + "\"");
        }
    }
}
