package io.descoped.stride.application.server;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Rank;
import org.jvnet.hk2.annotations.Service;

@Service(name = "jersey.objectMapper")
public class ObjectMapperFactory implements Factory<ObjectMapper> {
    private final static ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ObjectMapperFactory() {
    }

    @Override
    @Rank(-1)
    @Singleton
    public ObjectMapper provide() {
        return objectMapper;
    }

    @Override
    public void dispose(ObjectMapper instance) {
    }
}
