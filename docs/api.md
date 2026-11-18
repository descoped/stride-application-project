# stride-application-api

This module holds the contract an application programs against: `StrideApplication` (create, start and stop an
application), `Components` (the ready-made objects you hand to Stride) and the `ApplicationInitializer` SPI that
connects the contract to a runtime. It contains no server. The runtime is [`stride-application-core`](core.md),
which depends on this module, so an application that depends on core already has it. Depend on the API module
directly only for code that should compile against the contract without the runtime, such as a library or a
replacement runtime.

```xml
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The module brings `stride-application-config` (see [config.md](config.md)), the Jakarta Servlet API, `hk2-api`,
`slf4j-api` and `jul-to-slf4j`. Its Java module name is `stride.application.api`. It exports two packages:

| Package | Contents |
|---|---|
| `io.descoped.stride.application.api` | `StrideApplication`, `Components`, `Logging` |
| `io.descoped.stride.application.spi` | `ApplicationInitializer` |

The module reads no configuration keys of its own. The keys named in this document are declared by other modules;
[config.md](config.md) explains how keys are declared and overridden.

## Create and start an application

An application needs a configuration and, usually, the objects it has built itself. `create` builds the
application without starting anything. `start` brings up the services and the HTTP listener, and returns when the
listener is up.

```java
import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;

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

The sample application starts this way; [sample.md](sample.md) walks through all of it.

There are three `create` methods:

| Method | Configuration | Components |
|---|---|---|
| `StrideApplication.create()` | `ApplicationConfiguration.builder().defaults().build()` | none |
| `StrideApplication.create(configuration)` | yours | none |
| `StrideApplication.create(configuration, components)` | yours | yours |

`create` needs a runtime on the class path. Without one it throws a `RuntimeException` with the message
`Missing SPI for io.descoped.stride.application.spi.ApplicationInitializer`. See
[Provide a runtime](#provide-a-runtime-applicationinitializer).

## Stop an application

`stop()` and `close()` do the same thing. `StrideApplication` is `AutoCloseable`, so an application that should
live for one block of code can use try-with-resources:

```java
try (StrideApplication application = StrideApplication.create(configuration, components)) {
    application.start();
    // use the application
}
```

Stopping takes the application down through the run levels to 0 and then shuts down its service locator. The
server stops accepting requests, and requests that are running get `services.jetty.server.config.stopTimeout`
(default `PT30S`) to finish; [core.md](core.md) describes the shutdown in detail. Calling `stop()` or `close()`
again does nothing.

A stopped application cannot be started again. Create a new one. After `stop()`, the methods `activate()`,
`start()`, `proceedTo(..)` with a level above 0, `getHost()`, `getPort()` and `getLocalPort()` throw
`IllegalStateException`, because the service locator is shut down.

A failure while starting is thrown from `start()` as a `RuntimeException`. Call `close()` then too, so that
whatever did start is stopped; try-with-resources does it for you.

## Start in steps

Services start by run level. A run level is a number: when the application proceeds to a level, every service at
that level or below is started. Application services use the levels up to 9, the web server is assembled at 10 and
the HTTP listener starts at 11.

| Method | What it does |
|---|---|
| `activate()` | Proceeds to run level 9: the application's own services are started, the web server is not. |
| `proceedTo(int runLevel)` | Proceeds to the given run level, up or down. |
| `start()` | Proceeds to the highest run level any service declares. That is 11 by default, or higher if a service of yours is above 11. |
| `stop()`, `close()` | Proceeds to run level 0 and shuts the application down. |

`proceedTo` has two special cases. A level of 0 or less on an application that is above level 0 is the same as
`stop()`. The highest run level is the same as `start()`.

Use `activate()` when something must happen after your services are up and before the first request can arrive:

```java
try (StrideApplication application = StrideApplication.create(configuration)) {
    application.activate();                 // services up to run level 9
    // migrate a database, warm a cache ...
    application.start();                    // web server and listener
}
```

How to give a service a run level is described in [config.md](config.md) and [core.md](core.md).

## Read the state and the address

| Method | Returns |
|---|---|
| `isRunning()` | `true` from the first `activate()`, `proceedTo(..)` or `start()` until the application is stopped. `false` on a newly created application. |
| `isCompleted()` | `true` once the application is stopped. |
| `getHost()` | The host the listener is configured with: `services.jetty.server.config.host`, default `localhost`. |
| `getPort()` | The port the listener is configured with: `services.jetty.server.config.port`, default `0`. |
| `getLocalPort()` | The port the listener is actually bound to. |

The default port `0` lets the operating system pick a free port. `getPort()` then returns `0` and
`getLocalPort()` returns the port that was picked, so use `getLocalPort()` to build an address:

```java
application.start();
log.info("Listening on http://{}:{}/", application.getHost(), application.getLocalPort());
```

Read the address after `start()`. Before the web server is assembled, for example after `activate()`,
`getPort()` and `getLocalPort()` return `-1`, `getHost()` returns `localhost` whatever the configuration says, and
each call logs an error saying the server is not started.

## Hand ready-made objects to Stride

`Components` carries the objects the application built itself. You wire your object graph with constructors and
pass the results in. No injection is involved, and the classes need no annotations other than the JAX-RS ones on a
resource.

```java
Components components = Components.builder()
        .resource(new OrderResource(orders))            // a JAX-RS resource
        .filter("audit", new AuditFilter(auditLog))     // a servlet filter, known by its name
        .servlet("metrics", new MetricsServlet(meters)) // a servlet; needs servlets.metrics.config.pathSpec
        .service(Orders.class, orders)                  // injectable by those who use injection
        .build();

StrideApplication application = StrideApplication.create(configuration, components);
```

`OrderResource`, `AuditFilter`, `MetricsServlet` and `Orders` stand for classes of your own.

Three rules apply to everything in `Components`:

- **Stride never creates or closes these objects.** Their lifecycle belongs to the application. If an object
  holds a connection or a thread, close it yourself after `application.close()`.
- **An object handed in is always registered.** Code decides which components exist; configuration cannot remove
  one. If you want a switch, read your own configuration and leave the object out.
- **`Components` is immutable.** `build()` copies what the builder holds. `Components.none()` is the empty set,
  used by the `create` methods that take no components.

### Register a resource

`resource(Object)` registers a JAX-RS resource, provider or feature instance with Jersey. Jersey injects `@Context`
and `@Inject` members of a registered instance, as it does for any registered object; the constructor is yours.

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

```java
Components components = Components.builder()
        .resource(new GreetingResource(greetings))
        .build();
```

Resource instances are served by the Jersey service. They are not served when `services.jersey.server.enabled`
(default `true`) is `false`.

### Declare a filter

`filter(String name, jakarta.servlet.Filter filter)` registers a servlet filter under a name. Registering two
filters under one name throws `IllegalArgumentException`.

```java
Components components = Components.builder()
        .filter("audit", new AuditFilter(auditLog))
        .build();
```

With nothing else, the filter applies to every request (`/*`, dispatcher type `REQUEST`) at order 1000.

To place or scope the filter, declare an entry of the same name in the configuration. The instance is used; the
entry supplies the settings:

```java
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.config.Filters;

ApplicationConfiguration configuration = ApplicationConfiguration.builder()
        .defaults()
        .filters(Filters.builder()
                .filter(Filter.builder("audit").order(500).pathSpec("/orders/*")))
        .build();
```

An entry declared with `Filter.builder(name)` also makes the settings overridable from outside, by environment
variable and system property, even for settings the builder did not set:

| Key | Meaning | Value without an entry |
|---|---|---|
| `filters.<name>.config.order` | Position in the chain | `1000` |
| `filters.<name>.config.pathSpec` | The paths the filter applies to | `/*` |
| `filters.<name>.config.dispatches.<n>` | Dispatcher types | `REQUEST` |

For example `-Dfilters.audit.config.order=50` moves the filter above ahead of CORS.

`filters.<name>.enabled=false` does not remove a filter instance. Stride registers it and logs a warning.
A servlet instance is mounted too when `servlets.<name>.enabled` is `false`, without a warning.

Filters belong to the server, not to Jersey. They apply to every servlet and to `/ping`, also when
`services.jersey.server.enabled` is `false`.

#### Filter order

Filters run in ascending order, then by name. This is the only ordering rule.

| Filter | Order |
|---|---|
| CORS (`filters.cors.config.order`, see [core.md](core.md)) | `100` |
| Authentication (`filters.security.config.order`, see [security.md](security.md)) | `200` |
| A filter without an order | `1000` |

Set `filters.<name>.config.order` to place yours. The CORS filter answers a preflight request itself, so a filter
after it never sees that request; give a filter an order below 100 if it must. An order that is not a whole
number fails at startup with a message naming the key.

### Declare a servlet

`servlet(String name, jakarta.servlet.Servlet servlet)` registers a servlet under a name. A servlet has no default
path: the configuration must supply `servlets.<name>.config.pathSpec`. Registering two servlets under one name
throws `IllegalArgumentException`.

```java
import io.descoped.stride.application.config.Servlet;
import io.descoped.stride.application.config.Servlets;

Components components = Components.builder()
        .servlet("hello", new HelloServlet("hi there"))
        .build();

ApplicationConfiguration configuration = ApplicationConfiguration.builder()
        .defaults()
        .servlets(Servlets.builder().servlet(Servlet.builder("hello").pathSpec("/hello")))
        .build();
```

A servlet instance without a path stops the start with the message
`Servlet 'hello' has no path: set servlets.hello.config.pathSpec`.

Unlike a filter, a servlet gets no implied settings from its builder: `servlets.<name>.config.pathSpec` can be
overridden by environment variable or system property only when the builder sets `pathSpec(..)` or the key is
declared in a defaults file.

Servlets are mounted by the Jersey service. They are not mounted when `services.jersey.server.enabled` is `false`.

### Offer an instance for injection

`service(contract, instance)` makes an instance available for injection under the type `contract`.
`service(contract, name, instance)` also gives it a name, for `@Named` injection points. Neither argument may be
`null`, except the name.

You need this only when something created by Stride should receive the object: a resource, filter, servlet or
service that is named by class in the configuration. Objects you wire by hand do not need it.

```java
record Prices(Map<String, Integer> byItem) { }

@Path("/injected")
public class InjectedResource {
    private final Prices prices;

    @Inject
    public InjectedResource(Prices prices) {
        this.prices = prices;
    }
    // ...
}
```

```java
Components components = Components.builder()
        .service(Prices.class, new Prices(Map.of("tea", 30)))
        .build();

ApplicationConfiguration configuration = ApplicationConfiguration.builder()
        .defaults()
        .resources(Resources.builder().resource(Resource.builder("injected").clazz(InjectedResource.class)))
        .build();
```

The instance is bound as it is. Stride does not inject into it and does not destroy it.

Stride also offers three objects of its own for injection: the `ApplicationConfiguration`, the `Components` and
the `StrideApplication`.

## Let Stride create the classes instead

Handing in instances is one of two ways. Classes can instead be named in the configuration and created for you,
with HK2 injection (HK2 is the dependency injection library Stride's runtime is built on):

```java
ApplicationConfiguration configuration = ApplicationConfiguration.builder()
        .defaults()
        .resources(Resources.builder().resource(Resource.builder("orders").clazz(OrderResource.class)))
        .filters(Filters.builder().filter(Filter.builder("audit").clazz(AuditFilter.class).order(500)))
        .build();
```

The two ways can be mixed in one application. When a filter or servlet has both a class in the configuration and
an instance of the same name in `Components`, the instance is used and the class is not created. A filter entry
that is enabled and has neither a class nor an instance stops the start, with a message naming
`filters.<name>.config.class`.

The builders and the keys behind them are described in [config.md](config.md); how Stride creates and injects the
classes is described in [core.md](core.md).

## Look up a service

`getServiceLocator()` returns the application's HK2 `ServiceLocator`, the registry of everything Stride can
inject. It is there for applications that use injection and for tests; an application wired by hand does not need
it.

```java
application.start();
Prices prices = application.getServiceLocator().getService(Prices.class);
```

Look services up after the application has been activated or started. The instances from `Components` and the
services named in the configuration are added to the locator at the first `activate()`, `proceedTo(..)` or
`start()`.

## Logging

Stride logs through SLF4J and ships no backend; add the one you use. `Logging.init()` routes `java.util.logging`
to SLF4J: it resets the `java.util.logging` configuration, removes the handlers of the root logger and installs
the `jul-to-slf4j` bridge. This happens once, when the `Logging` class is loaded. The runtime in
`stride-application-core` calls `Logging.init()` when its application class is loaded, so you do not call it
yourself. Calling it again has no effect.

## Provide a runtime: `ApplicationInitializer`

`StrideApplication.create` does not know the runtime. It finds one with `java.util.ServiceLoader`, through this
interface:

```java
package io.descoped.stride.application.spi;

public interface ApplicationInitializer {

    StrideApplication initialize(ApplicationConfiguration configuration, Components components);

}
```

`stride-application-core` provides the implementation, `io.descoped.stride.application.core.StrideApplicationInitializer`.
With core on the class path or module path there is nothing to do.

You implement the interface only to replace the runtime. The implementation receives the configuration and the
components exactly as they were passed to `create`, and returns the `StrideApplication`. Register it in one of
the two ways `ServiceLoader` supports:

- on the class path, a file `META-INF/services/io.descoped.stride.application.spi.ApplicationInitializer`
  containing the name of the implementation class;
- on the module path, a `provides` clause in `module-info.java`:

  ```java
  provides io.descoped.stride.application.spi.ApplicationInitializer with com.example.MyInitializer;
  ```

`create` uses the first implementation `ServiceLoader` finds. Keep exactly one on the class path: with several,
which one is used is not defined by Stride.

## Reference

### `StrideApplication`

Package `io.descoped.stride.application.api`. Extends `AutoCloseable`.

| Method | Description |
|---|---|
| `static StrideApplication create()` | An application with the default configuration and no components. |
| `static StrideApplication create(ApplicationConfiguration)` | An application with no components. |
| `static StrideApplication create(ApplicationConfiguration, Components)` | An application with the objects it wired itself. |
| `void activate()` | Proceed to run level 9, below the web server. |
| `void proceedTo(int runLevel)` | Proceed to a run level. 0 or less stops; the highest level starts. |
| `void start()` | Proceed to the highest run level. |
| `void stop()` | Proceed to run level 0 and shut down. |
| `void close()` | Same as `stop()`. |
| `boolean isRunning()` | Started, at any run level, and not stopped. |
| `boolean isCompleted()` | Stopped. |
| `String getHost()` | The configured host of the listener. |
| `int getPort()` | The configured port of the listener; `-1` before the server is assembled. |
| `int getLocalPort()` | The bound port of the listener; `-1` before the server is assembled. |
| `ServiceLocator getServiceLocator()` | The application's HK2 service locator. |

### `Components`

Package `io.descoped.stride.application.api`. Final and immutable.

| Method | Description |
|---|---|
| `static Components.Builder builder()` | A new, empty builder. |
| `static Components none()` | The empty set of components. |
| `List<ServiceInstance<?>> services()` | The instances offered for injection, in the order added. |
| `List<Object> resources()` | The JAX-RS instances, in the order added. |
| `Map<String, Filter> filters()` | The filter instances by name. |
| `Map<String, Servlet> servlets()` | The servlet instances by name. |

`Filter` and `Servlet` are the `jakarta.servlet` types here, not the configuration types of the same names.

`Components.ServiceInstance<T>` is a record of `Class<T> contract`, `String name` (`null` when unnamed) and
`T instance`.

### `Components.Builder`

| Method | Description | Rejects |
|---|---|---|
| `resource(Object instance)` | A JAX-RS resource, provider or feature. | `null` |
| `filter(String name, Filter filter)` | A servlet filter known by `name`. | `null`; a name used twice |
| `servlet(String name, Servlet servlet)` | A servlet known by `name`. | `null`; a name used twice |
| `service(Class<T> contract, T instance)` | An instance injectable as `contract`. | `null` |
| `service(Class<T> contract, String name, T instance)` | As above, qualified by `name`. | `null` contract or instance |
| `build()` | The immutable `Components`. | |

A `null` argument throws `NullPointerException`; a name used twice throws `IllegalArgumentException`.

### Run levels

| Run level | What starts |
|---|---|
| up to 9 | The application's services. `activate()` stops here. |
| 10 | The web server is assembled: filters, servlets, Jersey and the resources. |
| 11 | The HTTP listener starts and requests are accepted. |
| above 11 | Services of yours that need the listener to be up. `start()` includes them. |

### Configuration keys named in this document

| Key | Default | Declared by |
|---|---|---|
| `services.jetty.server.config.host` | `localhost` | config ([config.md](config.md)) |
| `services.jetty.server.config.port` | `0` | config |
| `services.jetty.server.config.stopTimeout` | `PT30S` | config |
| `services.jersey.server.enabled` | `true` | config |
| `filters.cors.config.order` | `100` | config |
| `filters.security.config.order` | `200` | security ([security.md](security.md)) |

`filters.<name>.config.*` and `servlets.<name>.config.pathSpec` for your own components have no entry in a
defaults file. The filter keys exist once you declare the filter with `Filter.builder(name)`; the servlet key
exists once `Servlet.builder(name).pathSpec(..)` sets it. Either can also be declared in your own
`META-INF/stride/defaults.properties`.

## See also

- [config.md](config.md): configuration sources, keys and the component builders.
- [core.md](core.md): the runtime, run levels, the server, CORS and shutdown.
- [testing.md](testing.md): starting an application with `Components` in a test.
- [security.md](security.md) and [openapi.md](openapi.md): the optional modules.
- [sample.md](sample.md): a complete application.
