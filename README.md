# Stride Application

> "Stride" is a verb that means to walk or run with long, confident steps. The noun form of the word, "stride," refers
> to one of these long steps. The word is often used to describe a purposeful, confident, and determined manner of
> moving forward.

Stride is a small Java application stack for microservices and small monoliths: an HTTP server, REST, a lifecycle,
configuration and a test library. It decides as little as possible for you.

- **Small.** The runtime is Jetty, Jersey and HK2. Security and OpenAPI are separate modules you add when you use them.
- **Your libraries.** Stride does not pick persistence, messaging, clients, validation or a logging backend.
- **Your wiring.** Build your objects with `new` and hand them to Stride. Injection is there when you want it, never
  required.
- **Real tests.** The test library starts the real application and sends real HTTP requests.
- **Current Java.** Java 25 is the baseline. Every request runs on its own virtual thread.

Stride is not Spring or Micronaut, and not a Jakarta EE platform.

## Requirements

Java 25 or newer, and Maven.

## Artifacts

All artifacts have the group id `io.descoped.stride.application`.

| Artifact | Use it for | Documentation |
|---|---|---|
| `stride-application-core` | The runtime. Brings `stride-application-api` and `stride-application-config`. | [Runtime](docs/core.md), [lifecycle and components](docs/api.md), [configuration](docs/config.md) |
| `stride-application-junit-jupiter` | Tests: the JUnit extension and HTTP test client. Test scope. | [Testing](docs/testing.md) |
| `stride-application-security` | Optional. JWT bearer authentication for a resource server. | [Authentication](docs/security.md) |
| `stride-application-openapi` | Optional. Serves the OpenAPI description of your resources. | [OpenAPI](docs/openapi.md) |
| `stride-application-parent` | The parent POM of the artifacts above; you do not need it. | |

```xml
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-junit-jupiter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

There is no release yet, and `0.1.0-SNAPSHOT` is not published. Until the first release, build it into your
local repository with `mvn install`.

Stride logs through SLF4J and ships no backend. Add the one you use, for example `slf4j-simple` or Logback.

## Getting started

[`stride-application-sample`](stride-application-sample) is a working application that the build compiles and tests.
The code below is condensed from it.

Your own logic is a plain class:

```java
public final class Greetings {
    private final String salutation;

    public Greetings(String salutation) {
        this.salutation = salutation;
    }

    public Greeting greet(String name) {
        return new Greeting(salutation + ", " + name + "!");
    }

    public record Greeting(String message) {
    }
}
```

A REST resource is a JAX-RS class that gets what it needs through its constructor:

```java
@Path("/greetings")
public final class GreetingResource {
    private final Greetings greetings;

    public GreetingResource(Greetings greetings) {
        this.greetings = greetings;
    }

    @GET
    @Path("{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Greetings.Greeting greet(@PathParam("name") String name) {
        return greetings.greet(name);
    }
}
```

The application reads its configuration, wires its objects and starts:

```java
public static void main(String[] args) {
    ApplicationConfiguration configuration = ApplicationConfiguration.builder().defaults().build();

    Greetings greetings = new Greetings(configuration.requireString("greeting.salutation"));
    Components components = Components.builder()
            .resource(new GreetingResource(greetings))
            .build();

    StrideApplication application = StrideApplication.create(configuration, components);
    Runtime.getRuntime().addShutdownHook(new Thread(application::close, "shutdown"));
    application.start();
}
```

```
$ curl http://localhost:8080/greetings/world
{"message":"Hello, world!"}
```

`GET /ping` answers 200 on every Stride application, for health checks.

## Testing your application

The test library starts the real application on a free port and sends real HTTP requests:

```java
@ExtendWith(StrideExtension.class)
class PingTest {

    @Test
    void theApplicationAnswers(TestClient client) {
        client.get("/ping").expect200Ok();
    }
}
```

[Testing](docs/testing.md) shows how to test your own configuration and components.

## Documentation

The developer documentation is in [`docs/`](docs/README.md), one document for each module:

- [The runtime](docs/core.md): services, REST resources, servlets, filters, run levels, threads, shutdown.
- [Lifecycle and components](docs/api.md): starting and stopping, and handing Stride the objects you built.
- [Configuration](docs/config.md): keys, layers, environment variables, declaring components in code.
- [Testing](docs/testing.md): the JUnit extension and the test client.
- [Authentication](docs/security.md): JWT bearer authentication.
- [OpenAPI](docs/openapi.md): serving the OpenAPI description.
- [The sample application](docs/sample.md): a complete application to copy from.

What changes for an application between versions is in the [changelog](CHANGELOG.md).

## Building Stride

```
mvn clean verify
```

The build needs JDK 25 or newer. If your default JDK is older, point `JAVA_HOME` at a newer one.

- One module with what it depends on: `mvn verify -pl stride-application-core -am`
- One test class: add `-Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false`
- Into your local repository, for use from another project: `mvn install`

The tests run from an IDE as they are, with no extra setup.

## Contributing

Issues and pull requests are welcome at
[descoped/stride-application-project](https://github.com/descoped/stride-application-project).

- **Stride stays small.** Core holds only what every application needs; anything optional is its own module.
  Open an issue before adding a dependency, a module or a configuration key.
- **Tests start a real application** and assert on the status and the body of real requests.
- **One concern for each commit and pull request.** A change in behaviour or in a default gets a line in the
  [changelog](CHANGELOG.md).
- **Java 25, `jakarta.*` only**, and no preview features.

## License

[Apache License 2.0](LICENSE)
