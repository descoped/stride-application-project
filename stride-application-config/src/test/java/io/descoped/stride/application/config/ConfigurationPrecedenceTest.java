package io.descoped.stride.application.config;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Precedence, lowest first: Stride defaults, components declared in code, application sources (files, environment,
 * system properties), overrideProperty.
 */
class ConfigurationPrecedenceTest {

    public static class OrderService {
    }

    private static ApplicationConfiguration withSystemProperties(Map<String, String> properties,
                                                                 ApplicationConfiguration.Builder builder) {
        properties.forEach(System::setProperty);
        try {
            return builder.build();
        } finally {
            properties.keySet().forEach(System::clearProperty);
        }
    }

    @Test
    void stridesDefaultsApplyWithoutAnyApplicationSource() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder().build();

        assertEquals("localhost", configuration.server().host());
        assertEquals(0, configuration.server().port());
        assertEquals(10, configuration.server().minThreads());
        assertEquals(150, configuration.server().maxThreads());
        assertEquals(30_000, configuration.server().idleTimeout());
        assertEquals(30_000, configuration.server().stopTimeout());
        assertEquals(Optional.empty(), configuration.application().url());
        assertTrue(configuration.server().isHttp2Enabled());
        assertFalse(configuration.isVerboseLogging());
        assertEquals(EnumSet.of(DispatcherType.FORWARD, DispatcherType.REQUEST),
                configuration.filters().filter("cors").orElseThrow().dispatches());
    }

    @Test
    void systemPropertyOverridesAKeyThatOnlyStridesDefaultsDeclare() {
        ApplicationConfiguration configuration = withSystemProperties(Map.of(
                        "services.hk2.threadcount", "5",
                        "services.jetty.server.config.maxThreads", "64",
                        "filters.cors.config.origin", "https://app.example.test"),
                ApplicationConfiguration.builder().defaults());

        assertEquals(5, configuration.requireInt("services.hk2.threadcount"));
        assertEquals(64, configuration.server().maxThreads());
        assertEquals("https://app.example.test", configuration.requireString("filters.cors.config.origin"));
    }

    @Test
    void systemPropertyIsIgnoredWithoutAPresetOrSourceThatReadsIt() {
        ApplicationConfiguration configuration = withSystemProperties(
                Map.of("services.jetty.server.config.maxThreads", "64"),
                ApplicationConfiguration.builder());

        assertEquals(150, configuration.server().maxThreads());
    }

    @Test
    void componentDeclaredInCodeExistsAndItsValuesCanBeOverriddenFromOutside() {
        ApplicationConfiguration.Builder builder = ApplicationConfiguration.builder()
                .defaults()
                .services(Services.builder()
                        .service(Service.builder("orders")
                                .enabled(true)
                                .clazz(OrderService.class)
                                .runLevel(12)));

        Service declared = builder.build().services().service("orders").orElseThrow();
        assertTrue(declared.isEnabled());
        assertEquals(OrderService.class.getName(), declared.className());
        assertEquals(12, declared.runLevel());

        Service overridden = withSystemProperties(Map.of(
                        "services.orders.config.runLevel", "15",
                        "services.orders.enabled", "false"), builder)
                .services().service("orders").orElseThrow();
        assertEquals(15, overridden.runLevel());
        assertFalse(overridden.isEnabled());
        assertEquals(OrderService.class.getName(), overridden.className());
    }

    @Test
    void listDeclaredInCodeReplacesTheDefaultElementByElement() {
        ApplicationConfiguration configuration = ApplicationConfiguration.builder()
                .filters(Filters.builder()
                        .filter(Filter.builder("cors")
                                .dispatches(EnumSet.of(DispatcherType.ASYNC, DispatcherType.ERROR))))
                .build();

        Filter cors = configuration.filters().filter("cors").orElseThrow();
        assertEquals(EnumSet.of(DispatcherType.ASYNC, DispatcherType.ERROR), cors.dispatches());
        assertEquals("/*", cors.pathSpec(), "values the code did not set keep Stride's default");
        assertEquals(100, cors.order(), "declaring a filter in code does not reset what the defaults say");
    }

    @Test
    void everySettingOfAFilterDeclaredInCodeCanBeOverriddenFromOutside() {
        ApplicationConfiguration.Builder builder = ApplicationConfiguration.builder()
                .defaults()
                .filters(Filters.builder().filter(Filter.builder("audit")));

        Filter declared = builder.build().filters().filter("audit").orElseThrow();
        assertEquals("/*", declared.pathSpec());
        assertEquals(Filter.DEFAULT_ORDER, declared.order());
        assertEquals(EnumSet.of(DispatcherType.REQUEST), declared.dispatches());

        Filter overridden = withSystemProperties(Map.of(
                        "filters.audit.config.order", "7",
                        "filters.audit.config.pathSpec", "/api/*",
                        "filters.audit.enabled", "false"), builder)
                .filters().filter("audit").orElseThrow();
        assertEquals(7, overridden.order());
        assertEquals("/api/*", overridden.pathSpec());
        assertFalse(overridden.isEnabled());
    }

    @Test
    void overridePropertyWinsOverSystemProperty() {
        ApplicationConfiguration configuration = withSystemProperties(
                Map.of("services.jetty.server.config.port", "8081"),
                ApplicationConfiguration.builder()
                        .defaults()
                        .overrideProperty("services.jetty.server.config.port", "8082"));

        assertEquals(8082, configuration.server().port());
    }

    @Test
    void applicationSuppliedSourcesSitOnTopOfStridesDefaults() {
        ApplicationConfiguration configuration = withSystemProperties(
                Map.of("services.jetty.server.config.port", "8081"),
                ApplicationConfiguration.builder()
                        .configuration(sources -> sources
                                .value("services.jetty.server.config.host", "0.0.0.0")
                                .systemProperties()));

        assertEquals("0.0.0.0", configuration.server().host());
        assertEquals(8081, configuration.server().port());
        assertEquals(150, configuration.server().maxThreads());
    }
}
