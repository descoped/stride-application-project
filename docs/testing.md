# Testing a Stride application

`stride-application-junit-jupiter` is the test library. It starts your application as a real server on a free
port and gives the test an HTTP client for it. Add it, in test scope, to every project that builds an application
on Stride.

The idea behind it: a service is proven by real requests against a real, started server. The test does not call
resource methods directly and does not replace the server with a stand-in. A fake may supply data to the
application, for example a resource wired to an in-memory collaborator. The application under test stays real.

## Maven dependency

```xml
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-junit-jupiter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

The library brings `stride-application-api`, `junit-jupiter-api`, `junit-jupiter-engine` and `slf4j-simple` with
it. It does not bring the runtime: the application is created through `StrideApplication.create`, which needs
`stride-application-core` on the class path. An application built on Stride already has it. See
[core.md](core.md).

`slf4j-simple` is an SLF4J backend. A project whose tests log through another backend excludes it from this
dependency.

Everything is in one package, `io.descoped.stride.application.test.server`. The Java module is
`stride.application.junit.jupiter`.

## Test with the defaults

`StrideExtension` is a JUnit Jupiter extension. Put it on the class with `@ExtendWith` to start an application
with the test defaults and nothing handed in:

```java
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(StrideExtension.class)
class PingTest {

    @Test
    void answers(TestClient client) {
        client.get("/ping").expect200Ok();
    }
}
```

The application is built from `ApplicationConfiguration.builder().testDefaults()`, see
[The test defaults](#the-test-defaults). `/ping` is the health check every Stride server answers, see
[core.md](core.md).

## Test your own application

Register the extension in a field with `@RegisterExtension` and build it with `StrideExtension.builder()`. The
builder takes the configuration, the ready-made objects and the client timeout.

This is the test of the sample application, see [sample.md](sample.md):

```java
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void anUnknownPathIsNotFound(TestClient client) {
        client.get("/farewells/Ada").expect404NotFound();
    }
}
```

`GreetingApplication.configuration()` returns the `ApplicationConfiguration.Builder` the application also uses in
`main`. `GreetingApplication.components` is the method that wires the application's objects from an
`ApplicationConfiguration`. The test runs the same configuration and the same wiring as production, with one value
overridden.

### Configuration

`configuration(Supplier<ApplicationConfiguration.Builder>)` supplies the configuration builder. The supplier is
called for every start, so each application gets a builder of its own. Without it the extension uses
`ApplicationConfiguration.builder().testDefaults()`.

Use `overrideProperty` on the builder to set a value for the test. It is the highest configuration layer, see
[config.md](config.md).

The test library sets three keys itself, after the supplier has run, so values you give for them are replaced:

| Key | Value in a test | Default in `defaults.properties` |
|---|---|---|
| `services.jetty.server.config.host` | `localhost` | `localhost` |
| `services.jetty.server.config.port` | `0`: the operating system picks a free port | `0` |
| `services.jetty.server.config.context-path` | empty: the application is served at the root | `/` |

### Components

`Components` holds ready-made objects that the application hands to Stride: resources, services, filters and
servlets. It is described in [api.md](api.md). The builder accepts them in two forms:

- `components(Function<ApplicationConfiguration, Components>)` wires the objects from the configuration the
  application is started with, including what the test overrides. Use it to run the application's own wiring:
  `.components(MyApplication::components)`.
- `components(Supplier<Components>)` supplies objects that do not need the configuration.

Both are called for every start, so an application for each test gets fresh objects. Without either the
application gets `Components.none()`.

### Replace a collaborator with a fake

Hand in a resource wired to the fake. The server, the routing and the resource are the real ones; only the data
comes from the test.

```java
class OrderTest {

    @RegisterExtension
    static final StrideExtension stride = StrideExtension.builder()
            .components(() -> Components.builder().resource(new OrderResource(new FakeOrders())).build())
            .build();

    @Test
    void listsOrders(TestClient client) {
        client.get("/orders").expect200Ok();
    }
}
```

`OrderResource` and `FakeOrders` stand for your own classes. `Components` is
`io.descoped.stride.application.api.Components`.

### Client timeout

`timeout(Duration)` sets how long the injected `TestClient` waits. The default is `TestClient.DEFAULT_TIMEOUT`,
30 seconds. The duration must be positive: zero or a negative duration throws `IllegalArgumentException`, `null`
throws `NullPointerException`.

```java
@RegisterExtension
static final StrideExtension stride = StrideExtension.builder()
        .components(() -> Components.builder().resource(new GreetingResource()).build())
        .timeout(Duration.ofMillis(500))
        .build();
```

## One application for the class, or one for each test

Where the extension is registered decides how long an application lives.

| Registration | Application | Available in |
|---|---|---|
| `@ExtendWith(StrideExtension.class)` on the class | One for the class and its `@Nested` classes | Constructors, `@BeforeAll`, lifecycle methods, tests |
| `@RegisterExtension` in a `static` field | One for the class and its `@Nested` classes | Constructors, `@BeforeAll`, lifecycle methods, tests |
| `@RegisterExtension` in an instance field | A new one for every test | `@BeforeEach`, `@AfterEach`, tests |

An application for the class is started before `@BeforeAll` methods run and is stopped when the class is done.

An application for a test is started before the test and stopped after it, before the next test starts. It does
not exist yet when the test class is constructed or when `@BeforeAll` runs, so a parameter cannot be resolved
there. Use a static field to have the application in constructors and `@BeforeAll` methods.

```java
class CounterTest {

    @RegisterExtension
    final StrideExtension stride = StrideExtension.builder()
            .components(() -> Components.builder().resource(new CountResource()).build())
            .build();

    @RepeatedTest(3)
    void everyTestCountsFromOne(TestClient client) {
        assertEquals("1", client.get("/count").expect200Ok().body());
    }
}
```

In both cases the application is stopped and the client is closed also when a test fails. If the application
fails to start, it is closed and the failure is thrown on.

## Parameters a test can receive

Test methods, constructors and lifecycle methods may declare parameters of these types:

| Type | What the test gets |
|---|---|
| `TestClient` | The client for the running application, with the timeout of the extension |
| `TestApplication` | The running application |
| `StrideApplication` | The same object as `TestApplication`, by its lifecycle interface |
| `TestUriResolver` | The same object as `TestApplication`, by the interface that builds URLs |

```java
@Test
void theApplicationHasTheGivenConfiguration(TestClient client, TestApplication application) {
    ApplicationConfiguration configuration =
            application.getServiceLocator().getService(ApplicationConfiguration.class);

    assertEquals("configured", configuration.requireString("application.alias"));
    assertTrue(application.getLocalPort() > 0);
}
```

`getServiceLocator()` returns the HK2 `ServiceLocator` of the application, for a test that needs to look at a
service. `getLocalPort()` is the port the server listens on.

## Send requests

`TestClient` sends real HTTP requests with the JDK's `java.net.http.HttpClient`. A path such as `/greetings/Ada`
is resolved against the running application: `http://localhost:<port>/greetings/Ada`. The path includes the query
string, if any. It is written without the context path: the request goes under the context path the application
runs under, which is the root unless the test asks for another (see
[Run under a context path](#run-under-a-context-path)).

```java
client.get("/notes/7").expect200Ok();
client.post("/notes", "eggs").expect201Created();
client.put("/notes/7", "milk").expect200Ok();
client.delete("/notes/7").expect204NoContent();
```

Methods that take a `String` body send it as UTF-8. Methods that return `ResponseHelper<String>` read the response
body as UTF-8. `get`, `head`, `options`, `delete`, `post` and `put` each have a variant that takes an
`HttpResponse.BodyHandler<R>` for another body type.

### Headers

`get` and `options` take header names and values in turn after the path. A name may be repeated.

```java
client.get("/identity", "Authorization", "Bearer " + token).expect200Ok();

var response = client.options("/price/tea",
        "Origin", "https://app.example.test", "Access-Control-Request-Method", "GET").response();
```

`postJson` and `postForm` set the `Content-Type` to `application/json` and `application/x-www-form-urlencoded`.
They take a body publisher and a body handler:

```java
client.postJson("/notes/json",
        HttpRequest.BodyPublishers.ofString("{\"title\":\"tea\"}"),
        HttpResponse.BodyHandlers.ofString()).expect200Ok();
```

The other helpers send no headers of their own. Use `send` when a `PUT`, `POST`, `DELETE` or `HEAD` needs a
header.

### Any method, any header

`send` is the one method all helpers go through. It takes the method, the path, a body publisher, a body handler
and the headers.

```java
ResponseHelper<String> response = client.send("PATCH", "/notes/7",
        HttpRequest.BodyPublishers.ofString("{\"title\":\"tea\"}"),
        HttpResponse.BodyHandlers.ofString(),
        "Content-Type", "application/json", "If-Match", "\"3\"");
```

### Timeouts

Connecting, and every request up to its response headers, has a timeout. A request the application never answers
fails the test instead of hanging the build. A response whose body stalls after the headers is not covered.

The timeout is 30 seconds (`TestClient.DEFAULT_TIMEOUT`) unless another is given, with
`StrideExtension.builder().timeout(...)` or `TestClient.create(server, timeout)`. `client.timeout()` returns it.

### Failed requests

A request that cannot be sent, or that times out, throws `TestClientException`, an unchecked exception. The
message names the method and the path, for example `GET /greeting/never failed: ...`. The cause tells what
happened: a `java.net.http.HttpTimeoutException` for a timeout, another `IOException` otherwise.

```java
TestClientException failure = assertThrows(TestClientException.class, () -> client.get("/greeting/never"));

assertInstanceOf(HttpTimeoutException.class, failure.getCause());
```

If the test thread is interrupted while it waits, the exception message ends in `was interrupted` and the thread's
interrupt flag is set again.

A response with an error status is not a failed request. It comes back as a `ResponseHelper` like any other.

## Check the response

Every request returns a `ResponseHelper<T>`, where `T` is the type of the body. The `expect...` methods assert the
status code and return the helper, so calls chain. When the status differs, the test fails with an
`AssertionError` whose message contains the response body.

```java
assertEquals("post [eggs]", client.post("/notes", "eggs").expect201Created().body());

ResponseHelper<String> removed = client.delete("/notes/missing");
assertEquals(400, removed.expectAnyOf(404, 400).statusCode());
```

For a status that has no method of its own, use `expectAnyOf` with one or more codes, or assert on
`statusCode()`.

`response()` returns the `java.net.http.HttpResponse<T>`, for the headers:

```java
var response = client.get("/greetings/Ada").expect200Ok().response();

assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
```

### JSON bodies

`ResponseHelper` does not parse JSON. `body()` of a `get`, `post`, `put` or `delete` is the response text. Compare
it as a string, as the sample test does, or parse it with the JSON library your application uses.

```java
assertEquals("{\"message\":\"Hei, Ada!\"}", client.get("/greetings/Ada").expect200Ok().body());
```

## Control the lifecycle yourself

Use `TestApplication` without the extension when the test decides when the application starts and stops, for
example to bring it up in steps. `TestApplication` implements `StrideApplication`, described in [api.md](api.md),
and `TestUriResolver`.

`new TestApplication.Builder()` builds an application that is not started yet:

```java
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;

class GreetingLifecycleTest {

    @Test
    void answersOnceStarted() {
        ApplicationConfiguration.Builder configuration = GreetingApplication.configuration();

        try (TestApplication application = new TestApplication.Builder()
                .configuration(configuration)
                .components(GreetingApplication::components)
                .build()) {
            application.start();

            try (TestClient client = TestClient.create(application)) {
                client.get("/greetings/Ada").expect200Ok();
            }
        }
    }
}
```

`GreetingApplication.configuration()` and `components` are package-private, so the test is in the package of the
application.

`build()` sets the host, the port and the context path on the configuration builder it is given, then builds the
configuration and creates the application.

`TestApplication` and `TestClient` are both `AutoCloseable`. Close them yourself, with try-with-resources or in
`@AfterEach`; no extension does it here. `TestApplication.close()` stops the application. `TestClient.close()`
closes the connections and cuts off requests still in progress.

The lifecycle methods are those of `StrideApplication`:

```java
try (TestApplication application = new TestApplication.Builder()
        .configuration(ApplicationConfiguration.builder().testDefaults())
        .build()) {
    application.activate();                        // the application's services, not the server
    assertEquals(-1, application.getLocalPort());  // nothing listens yet

    application.start();                           // the server and the listener
    assertTrue(application.getLocalPort() > 0);
}
```

Run levels, `activate()` and `proceedTo(int)` are described in [core.md](core.md).

### Run under a context path

A test runs the application at the root, whatever the configuration says. To run it under a context path, set
it on the builder. The test still writes its paths without it:

```java
try (TestApplication application = new TestApplication.Builder().contextPath("/api").build()) {
    application.start();

    try (TestClient client = TestClient.create(application)) {
        client.get("/ping").expect200Ok();     // sent to /api/ping
    }
}
```

`contextPath(null)` keeps the context path of the configuration, and the requests go under that one.

### A client for any server

`TestClient.create` takes a `TestUriResolver`, an interface with one method that turns a path into a URL. A
`TestApplication` is one. A lambda is another, when the test wants to address a server by itself:

```java
int port = application.getLocalPort();
TestClient client = TestClient.create(uri -> "http://localhost:" + port + uri, Duration.ofSeconds(5));
```

`TestApplication.testURL(String)` returns `http://<host>:<port><context path><uri>`, with the host and the
listening port of the application and the context path it runs under. An IPv6 host is put in brackets. With a
context path of `/api`, `testURL("/ping")` ends in `/api/ping`. At the root nothing is added.

## The test defaults

`ApplicationConfiguration.builder().testDefaults()` is the configuration preset for tests. It reads, lowest first:

1. the `META-INF/stride/defaults.properties` of every module on the class path;
2. `application.properties` from the class path;
3. `test_override.properties` from the class path;
4. environment variables, then system properties, for declared keys.

It is `defaults()` with `test_override.properties` from the class path in place of `local_override.properties`
from the working directory. Put `test_override.properties` in `src/test/resources` to give every test of a
project the same values. Values set with `overrideProperty` come on top. The layers and the rule that every key
must be declared are described in [config.md](config.md).

A test may use the application's production preset instead, as the sample test does with
`GreetingApplication.configuration()`, which calls `defaults()`. The host, port and context path are set by the
test library either way.

## The free port

The test library starts every application with `services.jetty.server.config.port` set to `0`, so the operating
system picks a free port. Tests never share a fixed port, and several applications can run at the same time.

Read the port with `getLocalPort()` once the application is started. Before the server is up it returns `-1`. A
test seldom needs it: `TestClient` resolves paths against the running application.

## Reference

### `StrideExtension.Builder`

| Method | Default | Meaning |
|---|---|---|
| `configuration(Supplier<ApplicationConfiguration.Builder>)` | `ApplicationConfiguration.builder().testDefaults()` | The configuration builder; called for every start |
| `components(Supplier<Components>)` | `Components.none()` | Ready-made objects; called for every start |
| `components(Function<ApplicationConfiguration, Components>)` | `Components.none()` | Ready-made objects wired from the configuration; called for every start |
| `timeout(Duration)` | `TestClient.DEFAULT_TIMEOUT` | Timeout of the injected client; must be positive |
| `build()` | | The extension |

`new StrideExtension()`, which `@ExtendWith` uses, is the builder with all defaults.

### `TestApplication.Builder`

| Method | Default | Meaning |
|---|---|---|
| `configuration(ApplicationConfiguration.Builder)` | `ApplicationConfiguration.builder().testDefaults()` | The configuration builder |
| `components(Components)` | `Components.none()` | Ready-made objects |
| `components(Function<ApplicationConfiguration, Components>)` | `Components.none()` | Ready-made objects wired from the built configuration |
| `host(String)` | `localhost` | Overrides `services.jetty.server.config.host`; `null` leaves the configured value |
| `port(int)` | `-1` | Overrides `services.jetty.server.config.port`; a negative value becomes `0`, a free port |
| `contextPath(String)` | empty: the root | The context path the application runs under; overrides `services.jetty.server.config.context-path`. `null` keeps the configured value. The test's requests go under it |
| `build()` | | A `TestApplication` that is not started |

### `TestApplication`

| Method | Meaning |
|---|---|
| `start()` | Starts the application, server and listener included |
| `activate()` | Starts the application's services, not the server |
| `proceedTo(int runLevel)` | Proceeds to a run level |
| `stop()`, `close()` | Stops the application |
| `isRunning()`, `isCompleted()` | The state of the lifecycle |
| `getServiceLocator()` | The HK2 `ServiceLocator` of the application |
| `getHost()` | The host the server listens on |
| `getLocalPort()` | The port the server listens on; `-1` when no server is up |
| `getPort()` | The configured port of the listener, `0` unless the test set another; `-1` when no server is up |
| `testURL(String uri)` | `http://<host>:<port><context path><uri>` |

### `TestClient`

| Method | Request |
|---|---|
| `TestClient.create(TestUriResolver)` | A client with `DEFAULT_TIMEOUT` |
| `TestClient.create(TestUriResolver, Duration)` | A client with the given timeout |
| `get(uri, headers...)` | `GET` |
| `head(uri)` | `HEAD` |
| `options(uri, headers...)` | `OPTIONS` |
| `post(uri)`, `post(uri, body)` | `POST` without or with a text body |
| `postJson(uri, bodyPublisher, bodyHandler)` | `POST` with `Content-Type: application/json` |
| `postForm(uri, bodyPublisher, bodyHandler)` | `POST` with `Content-Type: application/x-www-form-urlencoded` |
| `put(uri)`, `put(uri, body)` | `PUT` without or with a text body |
| `delete(uri)` | `DELETE` |
| `send(method, uri, bodyPublisher, bodyHandler, headers...)` | Any method |
| `timeout()` | The timeout of the client |
| `getTestUriResolver()` | The resolver the client was created with |
| `close()` | Closes the connections; requests in progress are cut off |

`get`, `head`, `options` and `delete` also take a `HttpResponse.BodyHandler<R>` after the path. `post` and `put`
also take a `HttpRequest.BodyPublisher` and a `HttpResponse.BodyHandler<R>`.

### `ResponseHelper<T>`

| Method | Meaning |
|---|---|
| `new ResponseHelper<>(HttpResponse<T>)` | Wraps a response the test got by other means |
| `expect200Ok()` | Asserts status 200 |
| `expect201Created()` | Asserts status 201 |
| `expect204NoContent()` | Asserts status 204 |
| `expect400BadRequest()` | Asserts status 400 |
| `expect404NotFound()` | Asserts status 404 |
| `expectAnyOf(int...)` | Asserts that the status is one of the given codes |
| `statusCode()` | The status code |
| `body()` | The response body |
| `response()` | The `java.net.http.HttpResponse<T>` |

### Other types

| Type | Role |
|---|---|
| `TestUriResolver` | One method, `String testURL(String uri)`: turns a path into the URL to call |
| `TestClientException` | Unchecked; thrown by `TestClient` when a request could not be sent, timed out or was interrupted |
