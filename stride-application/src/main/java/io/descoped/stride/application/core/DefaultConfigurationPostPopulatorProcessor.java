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
import java.util.Optional;
import java.util.function.Function;

record DefaultConfigurationPostPopulatorProcessor(ApplicationConfiguration configuration)
        implements PopulatorPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DefaultConfigurationPostPopulatorProcessor.class);

    @Override
    public DescriptorImpl process(ServiceLocator serviceLocator, DescriptorImpl descriptorImpl) {
        String name = descriptorImpl.getName();
        if (name == null) {
            logServiceStatus(descriptorImpl.getImplementation(), null, true);
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
