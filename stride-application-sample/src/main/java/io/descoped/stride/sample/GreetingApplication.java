package io.descoped.stride.sample;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A complete Stride application: configuration, objects wired by hand, a server.
 */
public final class GreetingApplication {

    private static final Logger log = LoggerFactory.getLogger(GreetingApplication.class);

    private GreetingApplication() {
    }

    /**
     * The defaults of every module on the class path, then {@code application.properties}, then environment
     * variables and system properties.
     */
    static ApplicationConfiguration.Builder configuration() {
        return ApplicationConfiguration.builder().defaults();
    }

    /**
     * The application wires its own objects and hands them to Stride. No injection is involved.
     */
    static Components components(ApplicationConfiguration configuration) {
        Greetings greetings = new Greetings(configuration.requireString("greeting.salutation"));
        return Components.builder()
                .resource(new GreetingResource(greetings))
                .build();
    }

    public static void main(String[] args) {
        ApplicationConfiguration configuration = configuration().build();
        StrideApplication application = StrideApplication.create(configuration, components(configuration));
        // close() lets requests that are running finish, then stops the server
        Runtime.getRuntime().addShutdownHook(new Thread(application::close, "shutdown"));
        application.start();
        log.info("Listening on http://{}:{}/greetings/world", application.getHost(), application.getLocalPort());
    }
}
