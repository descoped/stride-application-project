# The sample application

`stride-application-sample` is a small, complete application built on Stride. The build compiles and tests it, so
it always works against the current code. Read it to see how the pieces fit, and copy from it when you start your
own application.

It is an example, not a library. It is never published, so there is no Maven dependency to add.

## What it consists of

The Java code is in the package `io.descoped.stride.sample`.

| File | What it is |
|---|---|
| `Greetings` | The application's own logic. A plain class that knows nothing about Stride, HTTP or injection. |
| `GreetingResource` | A JAX-RS resource at `/greetings` and `/greetings/{name}`. It gets `Greetings` through its constructor. |
| `GreetingApplication` | The `main` class: builds the configuration, wires the objects, starts the server. |
| `META-INF/stride/defaults.properties` | Declares the application's own key, `greeting.salutation`, with the default `Hello`. |
| `application.properties` | What the application sets differently from the defaults: `application.alias=greetings` and `services.jetty.server.config.port=8080`. |
| `GreetingApplicationTest` | Starts the application on a free port and sends real requests. |
| `SecuredGreetingApplicationTest` | The same with authentication on, and tokens the test signs. |

Its dependencies are `stride-application-core`, which brings the configuration and API modules,
`stride-application-security` for authentication, a logging backend (`slf4j-simple`), and for the tests
`stride-application-junit-jupiter` and JUnit Jupiter. Stride logs through SLF4J and ships no backend, so the
application picks one.

### Wiring

The application wires its own objects with `new` and hands them to Stride. No injection is involved:

```java
static ApplicationConfiguration.Builder configuration() {
    return ApplicationConfiguration.builder().defaults();
}

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
```

`defaults()` reads the defaults of every module on the class path, then `application.properties` from the class
path, then `local_override.properties` from the working directory if there is one, then environment variables and
system properties. See [config.md](config.md) for the layers, [api.md](api.md) for
`StrideApplication` and `Components`, and [core.md](core.md) for the server.

### Its own configuration key

The sample declares `greeting.salutation=Hello` in its own `META-INF/stride/defaults.properties`, the same way a
Stride module declares its keys. Because the key is declared, it can be overridden from outside: by the
environment variable `GREETING_SALUTATION` or the system property `-Dgreeting.salutation=...`.

### Authentication

The sample has `stride-application-security` on its class path, and authentication is off, which is the module's
default. `GET /greetings` greets the caller: the subject of the access token when authentication is on, `stranger`
when it is off.

```java
@GET
@Produces(MediaType.APPLICATION_JSON)
public Greetings.Greeting greetCaller(@Context SecurityContext security) {
    Principal caller = security.getUserPrincipal();
    return greetings.greet(caller == null ? "stranger" : caller.getName());
}
```

To run the sample with authentication, set `security.enabled=true`, `security.issuer` and `security.audiences`.
Stride then creates the filter and reads the issuer's keys when it starts. Every request needs a token after
that, `/ping` included, unless its path is listed in `security.public-paths`.

`SecuredGreetingApplicationTest` turns authentication on without an issuer. It makes a key pair, hands the
application a filter built with `JwtTokenVerifier.withKeys` and the public half, and signs its tokens with the
private half. The filter is the real one, so the test proves the challenge for a request without a token, the
rejection of a token from another issuer, and the greeting by the token's subject. See
[security.md](security.md).

## Run the test

From the root of the repository:

```
mvn -B -ntp verify -pl stride-application-sample -am
```

`-am` also builds the modules the sample depends on. The build needs JDK 25 or newer.

The test starts the application as it runs in production: same configuration, same wiring. It changes one value,
the salutation. The test library overrides host and port, so the application listens on `localhost` and a free
port instead of 8080. `StrideExtension` is the JUnit extension that starts the application and injects a
`TestClient`: see [testing.md](testing.md). It is registered in a static field, so one application serves every
test of the class.

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
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void withoutAuthenticationThereIsNoCallerToName(TestClient client) {
        assertEquals("{\"message\":\"Hei, stranger!\"}", client.get("/greetings").expect200Ok().response().body());
    }

    @Test
    void anUnknownPathIsNotFound(TestClient client) {
        client.get("/farewells/Ada").expect404NotFound();
    }

    @Test
    void theServerAnswersItsHealthCheck(TestClient client) {
        client.get("/ping").expect200Ok();
    }
}
```

## Run the application

The main class is `io.descoped.stride.sample.GreetingApplication`. The simplest way is to run its `main` method
from your IDE.

The module configures no Maven plugin that runs it. From a shell, install Stride into your local repository, let
Maven write the class path, and start Java with it:

```
mvn -B -ntp install
cd stride-application-sample
mvn -B -ntp dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" io.descoped.stride.sample.GreetingApplication
```

The application listens on `localhost`, port 8080, and logs the address. Then:

```
curl http://localhost:8080/greetings/world
```

```json
{"message":"Hello, world!"}
```

`GET /ping` answers 200: it is the health check that core serves.

To change the salutation or the port without touching the code:

```
GREETING_SALUTATION=Hei java -Dservices.jetty.server.config.port=9090 -cp ... io.descoped.stride.sample.GreetingApplication
```

Stop it with Ctrl-C. The shutdown hook calls `close()`, which lets running requests finish and then stops the
server. How long they get is `services.jetty.server.config.stopTimeout`, by default `PT30S`.

## Not a published artifact

The sample is part of the build so that it stays correct, but it is never uploaded:

- the builds that publish leave it out with `-pl '!:stride-application-sample'`;
- the sample's own POM fails, in the first build phase, any build that has both the sample and the publishing
  profile `central`.

Do not depend on `stride-application-sample`. Copy from it.

## Reference

### Endpoints

| Request | Answer |
|---|---|
| `GET /greetings/{name}` | 200, `application/json`, `{"message":"<salutation>, <name>!"}` |
| `GET /greetings` | 200, `application/json`, `{"message":"<salutation>, <caller>!"}`. The caller is the token's subject, or `stranger` without authentication. |
| `GET /ping` | 200. Served by core, not by the sample. |
| Any other path | 404 |

### Configuration

| Key | Value | Where it is set |
|---|---|---|
| `greeting.salutation` | `Hello` | Declared by the sample in `META-INF/stride/defaults.properties` |
| `application.alias` | `greetings` | `application.properties`; the default is `default` |
| `services.jetty.server.config.port` | `8080` | `application.properties`; the default is `0`, a free port |
