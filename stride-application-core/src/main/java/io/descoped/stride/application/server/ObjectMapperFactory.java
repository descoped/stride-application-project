package io.descoped.stride.application.server;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Rank;
import org.jvnet.hk2.annotations.Service;

/**
 * Makes a Jackson {@code ObjectMapper} injectable in every application. HK2 finds this factory through the
 * metadata generated at build time ({@code META-INF/hk2-locator/default}); no code refers to it. Its rank is
 * below the default, so an {@code ObjectMapper} the application offers itself wins.
 */
@Service(name = "jersey.objectMapper")
public class ObjectMapperFactory implements Factory<ObjectMapper> {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ObjectMapperFactory() {
        // HK2 creates the factory; there is nothing to set up.
    }

    @Override
    @Rank(-1)
    @Singleton
    @Named("jersey.objectMapper") // the mapper carries the factory's name, so the factory's switch removes both
    public ObjectMapper provide() {
        return objectMapper;
    }

    @Override
    public void dispose(ObjectMapper instance) {
        // The one mapper lives as long as the application and holds nothing to release.
    }
}
