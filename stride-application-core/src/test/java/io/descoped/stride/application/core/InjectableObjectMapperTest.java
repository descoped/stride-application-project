package io.descoped.stride.application.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Stride offers a Jackson {@code ObjectMapper} for injection. Nothing in Stride's code refers to the factory that
 * provides it: HK2 finds it through the metadata generated at build time, so only a started application shows
 * that it is there.
 */
class InjectableObjectMapperTest {

    record Price(String item, int amount) {
    }

    /** A service created by Stride that asks for the mapper. */
    public static class PriceReader {
        private final ObjectMapper mapper;

        @Inject
        public PriceReader(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        Price read(String json) throws Exception {
            return mapper.readValue(json, Price.class);
        }
    }

    private static ApplicationConfiguration configuration() {
        return ApplicationConfiguration.builder().testDefaults()
                .services(Services.builder().service(Service.builder("prices").clazz(PriceReader.class).runLevel(5)))
                .build();
    }

    @Test
    void aServiceGetsAnObjectMapperThatIgnoresUnknownProperties() throws Exception {
        try (StrideApplication application = StrideApplication.create(configuration())) {
            application.start();

            PriceReader reader = application.getServiceLocator().getService(PriceReader.class);

            assertEquals(new Price("tea", 12), reader.read("{\"item\":\"tea\",\"amount\":12,\"currency\":\"NOK\"}"));
        }
    }

    @Test
    void anObjectMapperTheApplicationOffersItselfWins() {
        ObjectMapper own = new ObjectMapper();
        Components components = Components.builder().service(ObjectMapper.class, own).build();

        try (StrideApplication application = StrideApplication.create(configuration(), components)) {
            application.start();

            assertSame(own, application.getServiceLocator().getService(PriceReader.class).mapper);
        }
    }
}
