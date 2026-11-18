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

## Modules

All modules have the group id `io.descoped.stride.application`.

| Artifact | Use it for |
|---|---|
| `stride-application-core` | The runtime. Brings `stride-application-api` and `stride-application-config`. |
| `stride-application-junit5` | Tests: the JUnit extension and HTTP test client. Test scope. |
| `stride-application-security` | Optional. JWT bearer authentication for a resource server. |
| `stride-application-openapi` | Optional. Serves the OpenAPI description of your resources. |
| `stride-application-parent` | The parent POM of the modules above; you do not need it. |

```xml
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-junit5</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

There is no release yet, and `0.1.0-SNAPSHOT` is not published. Until the first release, build it into your
local repository with `mvn install`.

Stride logs through SLF4J and ships no backend. Add the one you use, for example `slf4j-simple` or Logback.

## A complete application

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

## Handing objects to Stride

`Components` carries the objects the application built itself. Stride never creates or closes them.

```java
Components components = Components.builder()
        .resource(new OrderResource(orders))            // a JAX-RS resource
        .filter("audit", new AuditFilter(auditLog))     // a servlet filter, known by its name
        .servlet("metrics", new MetricsServlet(meters)) // a servlet; needs servlets.metrics.config.pathSpec
        .service(Orders.class, orders)                  // injectable by those who use injection
        .build();
```

An object handed in is always registered; configuration cannot remove it. If you want a switch, read your own
configuration and leave the object out.

Classes can instead be named in the configuration and created by Stride, with HK2 injection:

```java
ApplicationConfiguration.builder()
        .defaults()
        .resources(Resources.builder().resource(Resource.builder("orders").clazz(OrderResource.class)))
        .filters(Filters.builder().filter(Filter.builder("audit").clazz(AuditFilter.class).order(500)));
```

### Filter order

Filters run in ascending `order`, then by name. CORS is 100, authentication is 200, and a filter without an order
is 1000. Set `filters.<name>.config.order` to place yours.

## Configuration

Configuration is a flat set of keys. The layers, lowest first:

1. `META-INF/stride/defaults.properties` of every module on the class path. These files declare every key and its
   default.
2. Components declared in code with the builders.
3. The application's sources. `defaults()` reads `application.properties` from the class path, then
   `local_override.properties` from the working directory, then environment variables, then system properties.
4. `overrideProperty(key, value)`.

Two rules keep it predictable:

- **Every key is declared.** Environment variables and system properties can only override a key that a defaults
  file or a builder declares. A misspelled variable is ignored, not silently accepted.
- **No defaults in code.** Read keys with `requireString`, `requireInt` or `requireBoolean`.

An environment variable is the key in upper case, with each character that is not a letter or digit replaced by
`_`: `services.jetty.server.config.context-path` is `SERVICES_JETTY_SERVER_CONFIG_CONTEXT_PATH`.

Declare your application's own keys in your own `META-INF/stride/defaults.properties`:

```properties
greeting.salutation=Hello
```

Now `GREETING_SALUTATION=Howdy` or `-Dgreeting.salutation=Howdy` overrides it. Two modules declaring the same key is
an error at startup. If you build a single jar with the Maven Shade plugin, append these files with an
`AppendingTransformer` for `META-INF/stride/defaults.properties`; do not let one replace the others.

The keys you are most likely to set:

| Key | Default | Meaning |
|---|---|---|
| `services.jetty.server.config.host` | `localhost` | The address to listen on. |
| `services.jetty.server.config.port` | `0` | The port; 0 picks a free one. |
| `services.jetty.server.config.context-path` | `/` | The path every route is under. |
| `services.jetty.server.config.stopTimeout` | `PT30S` | How long running requests get to finish at shutdown. |
| `services.jetty.server.config.http2.enabled` | `true` | HTTP/2 without TLS (h2c) next to HTTP/1.1. |
| `services.jetty.server.config.forwarded.enabled` | `false` | Trust `Forwarded` and `X-Forwarded-*` from a proxy. |
| `filters.cors.config.origin` | `*` | Allowed origins. |
| `application.url` | empty | The public address, used in the OpenAPI description. |

The full list, with comments, is in each module's
[`defaults.properties`](stride-application-config/src/main/resources/META-INF/stride/defaults.properties).

## Testing

`StrideExtension` starts the application on a free port and gives the test a client. Requests time out, so a test
cannot hang the build.

```java
class GreetingApplicationTest {

    @RegisterExtension
    static final StrideExtension stride = StrideExtension.builder()
            .configuration(() -> GreetingApplication.configuration().overrideProperty("greeting.salutation", "Hei"))
            .components(GreetingApplication::components)
            .build();

    @Test
    void greetsByNameWithTheConfiguredSalutation(TestClient client) {
        var response = client.get("/greetings/Ada").expect200Ok().response();

        assertEquals("{\"message\":\"Hei, Ada!\"}", response.body());
    }
}
```

- In a static field, one application serves the whole class. In an instance field, every test gets a new one.
- `@ExtendWith(StrideExtension.class)` starts an application with the test defaults and nothing handed in.
- `TestClient`, `TestApplication` and `StrideApplication` can be parameters of tests, constructors and lifecycle
  methods.
- `components(MyApplication::components)` runs the application's own wiring with the configuration the test
  started it with.
- To replace a collaborator, hand in a resource wired to a fake. The application under test stays real.

## Authentication

Add `stride-application-security` to make the application an OAuth resource server that accepts JWT access tokens.
It follows OAuth 2.1 and the JWT profile for access tokens (RFC 9068).

```properties
security.enabled=true
security.issuer=https://identity.example.com/realms/shop
security.audiences=https://api.example.com
security.required-scopes=orders:read
security.public-paths=/ping
```

- At startup the issuer's metadata and keys are fetched over HTTPS. If that fails, the application does not start.
- A request without a valid token gets 401 with a `WWW-Authenticate: Bearer` challenge; one without a required
  scope gets 403.
- The caller is available as the request's principal, and its `roles` claim drives `@RolesAllowed`.
- `security.required-scopes` is space separated. The other lists are comma separated.
- For an authorization server that does not issue RFC 9068 tokens, set `security.token-type=JWT`, and
  `security.scope-claim` if scopes are in another claim.
- Set `security.resource` to the https URL clients reach the application at, and the application publishes its
  protected resource metadata (RFC 9728). For `https://api.example.com/orders` the document is served without a
  token at `/.well-known/oauth-protected-resource/orders`, and every challenge names it in `resource_metadata`. It
  tells a client which authorization server and scopes to use. A token's audience is normally this same URL, so
  it is usually also one of `security.audiences`. The path is inside the application's context path:
  with a context path other than `/`, a proxy must route the well-known URL to it.

All keys are in the module's
[`defaults.properties`](stride-application-security/src/main/resources/META-INF/stride/defaults.properties).

## OpenAPI

Add `stride-application-openapi` and register its resource. The description of your resources is then served at
`/openapi.json` and `/openapi.yaml`.

```java
.resources(Resources.builder()
        .resource(Resource.builder("openapi").clazz(ApplicationOpenApiResource.class)))
```

## Runtime behaviour

- **Threads.** Each request runs on a new virtual thread, so blocking code is fine. Nothing limits the number of
  concurrent requests; add a filter with a low order if you need a ceiling.
- **Shutdown.** `close()` stops accepting requests, lets running ones finish within the stop timeout, then stops the
  services in reverse order.
- **Lifecycle.** Services start by run level: application services up to 9, the web server at 10, the listener
  at 11.

## Building Stride

```
mvn clean verify
```

The build needs JDK 25 or newer. If your default JDK is older, point `JAVA_HOME` at a newer one.

## License

[Apache License 2.0](LICENSE)
