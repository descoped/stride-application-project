# stride-application-core

`stride-application-core` is the runtime of a Stride application. It starts an HTTP server (Jetty 12.1, EE11, with
HTTP/2), serves REST resources (Jersey 4, JSON through Jackson), and starts and stops services in a fixed order
(HK2 4 run levels). Every application needs it. It brings [`stride-application-api`](api.md) and
[`stride-application-config`](config.md) with it.

## Maven dependency

```xml
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Core logs through SLF4J and ships no logging backend. Add the one you use, for example `slf4j-simple` or Logback.

## Start an application

An application builds its configuration, wires its own objects, hands them to Stride and starts. This is what
the [sample application](sample.md) does, written out as one method:

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
    // close() lets requests that are running finish, then stops the server
    Runtime.getRuntime().addShutdownHook(new Thread(application::close, "shutdown"));
    application.start();
}
```

- `ApplicationConfiguration` holds the configuration. See [config.md](config.md).
- `Components` holds the ready-made objects the application hands in. See [api.md](api.md).
- `StrideApplication.create(..)` finds core through the Java `ServiceLoader`. Nothing listens yet.
  `create(configuration)` is the same without `Components`, and `create()` also builds the configuration with
  `defaults()`.
- `start()` starts every service, assembles the web server and opens the listener. It returns when the
  application accepts requests.
- `getHost()` and `getLocalPort()` tell where it listens. The default port is `0`: the operating system picks a
  free one. Set `services.jetty.server.config.port` for a fixed port. `getPort()` returns the configured port,
  so it is 0 when the operating system picks.

If something cannot start, `start()` throws a `RuntimeException`. The reason is in its chain of causes. Close the
application afterwards.

### Run without a main method of your own

`io.descoped.stride.application.core.StrideApplicationImpl` has a `main` method. It builds the configuration
with `defaults()`, creates the application without `Components`, registers a shutdown hook, starts, and waits
until its thread is interrupted. Use it as the main class when everything the application consists of is named in
the configuration or found by HK2 (see [Add a service](#add-a-service)).

## Run levels

A run level is a number that says when a service starts. Stride starts services level by level, lowest first, and
stops them in the opposite order.

| Run level | What starts |
|---|---|
| up to 9 | The application's own services. |
| 10 | The web server is assembled: filters, `/ping`, servlets, REST resources. Nothing listens yet. |
| 11 | The listener opens. The application accepts requests. |
| above 11 | Services that need the listener to be up. |

The constant `RunLevelConstants.WEB_SERVER_RUN_LEVEL` (package `io.descoped.stride.application.server`) is 10.

Services of one run level start at the same time, each on its own virtual thread.
`services.hk2.threadcount` (default `20`) bounds how many start at once.

`StrideApplication` moves through the levels:

| Method | Effect |
|---|---|
| `activate()` | Proceeds to run level 9: the application's services run, the web server does not. |
| `start()` | Proceeds to the highest run level any service declares: 11, or higher when a service asks for it. |
| `proceedTo(n)` | Proceeds to run level `n`. With 0 or less on an application that has left run level 0 it stops it; on one that has not started it does nothing. |
| `stop()`, `close()` | Proceeds to run level 0 and shuts the application down. |
| `isRunning()` | True once the application has proceeded to a run level and has not been stopped. |
| `isCompleted()` | True once it has been stopped. |

`activate()` followed by `start()` is allowed. A stopped application cannot be started again: create a new one.
On a stopped application `start()`, `activate()` and `proceedTo(n)` above 0 throw an `IllegalStateException`
with the message `The application is stopped and cannot be started again; create a new one`. Stopping it again
does nothing.

Before the web server is assembled, and after the application is stopped, `getLocalPort()` and `getPort()`
return -1 and `getHost()` returns `localhost`.

## Add a service

A service is an object that lives as long as the application: a repository, a client, a cache. There are three
ways to add one. Pick the first unless you want Stride to create the object.

### Wire it yourself and hand it in

Build the object with `new` and pass it where it is needed. No run level and no injection are involved. Stride
never creates or closes it.

```java
OrderRepository orders = new OrderRepository(dataSource);
Components components = Components.builder()
        .resource(new OrderResource(orders))
        .service(OrderRepository.class, orders)            // only if something should get it injected
        .service(Rates.class, "norway", new Rates(25))     // offered by name
        .build();
```

`service(contract, instance)` is needed only when a class that Stride or Jersey creates should receive the object
by injection, or when it is a [constructor argument of a configured resource](#a-class-with-constructor-arguments).

### Let Stride create a class at a run level

Name the class in the configuration. Stride creates it once, at its run level, and injects its `@Inject`
constructor through HK2.

```java
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;

ApplicationConfiguration configuration = ApplicationConfiguration.builder()
        .defaults()
        .services(Services.builder()
                .service(Service.builder("prices").clazz(PriceReader.class).runLevel(5)))
        .build();
```

```java
public class PriceReader {
    private final ObjectMapper mapper;

    @Inject
    public PriceReader(ObjectMapper mapper) {
        this.mapper = mapper;
    }
}
```

Rules for such a class:

- Give it a run level. Use 1 to 9 for a service the web server depends on, and 12 or more for one that needs the
  listener. A service without a run level is not started with the application: it is created once, the first
  time something asks for it.
- It must not carry a scope annotation such as `@Singleton`, nor HK2's `@Service`. `StrideApplication.create`
  then throws an `IllegalStateException` whose message names the key `services.<name>.config.class`.
- A `@Named` class is bound under that name. Other qualifier annotations on the class are kept too.
- To be told when the application stops, implement `org.glassfish.hk2.api.PreDestroy`.

The builder writes the keys `services.<name>.enabled`, `services.<name>.config.class` and
`services.<name>.config.runLevel`.

### Let HK2 discover a `@Service`

HK2 also finds classes annotated with `org.jvnet.hk2.annotations.Service`. Core's own services are found this way.

```java
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.runlevel.RunLevel;
import org.jvnet.hk2.annotations.Service;

@Service(name = "orders.cache")
@RunLevel(5)
public class OrderCache implements PreDestroy {

    @Inject
    public OrderCache(ApplicationConfiguration configuration) {
    }

    @Override
    public void preDestroy() {
    }
}
```

HK2 reads the list of such classes from the file `META-INF/hk2-locator/default` on the class path. HK2's annotation
processor `org.glassfish.hk2:hk2-metadata-generator` writes that file when the class is compiled. Stride's own
build names it in the `annotationProcessorPaths` of the compiler plugin; do the same in your build. Without the
file the class is not found.

A `@Service` without `@RunLevel` is created when something first asks for it.

### Switch a service off

Every named service has a switch: `services.<name>.enabled`.

- A discovered `@Service(name = "orders.cache")` is left out when `services.orders.cache.enabled` is `false`.
  Without that key, `services.hk2.defaults.enabled` (default `true`) decides: with `false`, a discovered named
  service runs only when its own key is `true`. A `@Service` without a name is always on.
- The switch is the service's own key, with the whole name. A service named `server` is switched by
  `services.server.enabled`; `services.jetty.server.enabled` belongs to the service `jetty.server` and does not
  touch it. The named services of HK2 itself are switched by `services.hk2.<name>.enabled`.
- A service declared with `Service.builder(name)` is enabled unless `.enabled(false)` is called or
  `services.<name>.enabled=false` comes from a later configuration source. A disabled service is not created.
- An instance handed in through `Components` has no switch. Leave it out instead.

Values under `services.<name>.metadata` are added to the HK2 descriptor of a discovered service of that name.

### What can be injected

These are available to every class that HK2 or Jersey creates:

| Type | What it is |
|---|---|
| `ApplicationConfiguration` | The configuration the application was created with. |
| `Components` | The objects the application handed in. |
| `StrideApplication` | The application itself. |
| `org.glassfish.hk2.api.ServiceLocator` | The HK2 registry. Also returned by `StrideApplication.getServiceLocator()`. |
| `com.fasterxml.jackson.databind.ObjectMapper` | See [Use the ObjectMapper](#use-the-objectmapper). |
| `org.glassfish.hk2.api.messaging.Topic<T>` | See [Publish and receive events](#publish-and-receive-events). |
| Each `Components.service(..)` instance | By its contract, and by name when one was given. |
| Each configured or discovered service | By its class. |
| `org.eclipse.jetty.server.Server` | The Jetty server, from run level 10. |
| `org.eclipse.jetty.ee11.servlet.ServletContextHandler` | The servlet context, from run level 10. |

Nothing is obtainable only by injection: the same objects can be read from
`application.getServiceLocator().getService(Type.class)`.

## Use the ObjectMapper

Core offers one Jackson `ObjectMapper`. It ignores unknown properties when it reads JSON. The mapper is
injectable, and resources read and write JSON with the same one:

```java
@Inject
public PriceReader(ObjectMapper mapper) {
    this.mapper = mapper;
}
```

A resource that takes a JSON body therefore accepts a body with properties its class does not have.

To use your own mapper instead, hand it in. It wins over core's, for injection and for the JSON of the resources:
its naming strategy, modules and date format apply to request and response bodies.

```java
Components components = Components.builder()
        .service(ObjectMapper.class, new ObjectMapper())
        .build();
```

Resources use the mapper that an unqualified `@Inject ObjectMapper` gets. Offer a mapper for another format, such
as YAML or CBOR, under its own type and not under the contract `ObjectMapper`: otherwise the resources write their
JSON with it.

`services.jersey.objectMapper.enabled=false` removes core's mapper. When the application hands in none of its
own, no `ObjectMapper` is injectable, and Jersey reads and writes JSON with a mapper of its own, which answers
400 to a body with an unknown property.

## Publish and receive events

HK2 topic events are enabled. One service publishes on a `Topic`, another receives in a `@SubscribeTo` method.

```java
import org.glassfish.hk2.api.messaging.MessageReceiver;
import org.glassfish.hk2.api.messaging.SubscribeTo;
import org.glassfish.hk2.api.messaging.Topic;

public record OrderPlaced(String order) {
}

@Service
public class Orders {
    private final Topic<OrderPlaced> placed;

    @Inject
    public Orders(Topic<OrderPlaced> placed) {
        this.placed = placed;
    }

    void place(String order) {
        placed.publish(new OrderPlaced(order));
    }
}

@MessageReceiver
public class Billing {
    public void on(@SubscribeTo OrderPlaced event) {
    }
}
```

The subscriber carries `@MessageReceiver`. It is either a discovered class, which then carries `@Service` too, or
a class named in the configuration, as `Billing` here:

```java
.services(Services.builder().service(Service.builder("billing").clazz(Billing.class).runLevel(5)))
```

A subscriber receives events from the moment it exists. Give it a run level so that it exists when the
application has started.

## Add a REST resource

A REST resource is a JAX-RS class: `@Path` on the class, `@GET`, `@POST` and so on on its methods. Core registers
it with Jersey, which serves it under the context path. Objects returned from a method with
`@Produces(MediaType.APPLICATION_JSON)` are written as JSON by Jackson, with the mapper described in
[Use the ObjectMapper](#use-the-objectmapper).

### An instance

Create the resource yourself and hand it in. It gets what it needs through its constructor and carries no
injection annotations.

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

```
$ curl http://localhost:8080/greetings/world
{"message":"Hello, world!"}
```

`resource(..)` also takes a JAX-RS provider or feature instance. An instance is always registered. Method
parameters such as `@Context UriInfo` work as usual.

### A class

Name the class in the configuration and Jersey creates it. An `@Inject` constructor receives services, including
those handed in with `Components.service(..)`.

```java
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;

ApplicationConfiguration.builder()
        .defaults()
        .resources(Resources.builder()
                .resource(Resource.builder("injected").clazz(InjectedResource.class)));
```

```java
@Path("/injected")
public class InjectedResource {
    private final Prices prices;

    @Inject
    public InjectedResource(Prices prices) {
        this.prices = prices;
    }
}
```

`.enabled(false)` on the builder, or `resources.<name>.enabled=false` from a later source, leaves it out.

### A class with constructor arguments

A resource class can be named together with its constructor arguments. Each argument is a service, looked up by
type and name. Stride calls the constructor that takes exactly those types. The class needs no injection
annotations.

```java
import io.descoped.stride.application.config.Args;

ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
        .defaults()
        .resources(Resources.builder().resource(Resource.builder("vat")
                .clazz(VatResource.class)
                .args(Args.builder().arg(Rates.class, "norway"))));

Components components = Components.builder()
        .service(Rates.class, "norway", new Rates(25))
        .build();
```

```java
@Path("/vat")
public class VatResource {
    private final Rates rates;

    public VatResource(Rates rates) {
        this.rates = rates;
    }
}
```

The application does not start when:

- no service of that type and name exists. The message names the resource, the type and the name;
- no constructor takes those argument types. The message names the types;
- the constructor throws. The message carries the constructor's own failure.

### What core sets up in Jersey

- `@RolesAllowed` is enforced: core registers Jersey's `RolesAllowedDynamicFeature`. Who the caller is comes
  from the request. See [security.md](security.md).
- JSON is read and written with the injectable `ObjectMapper`. See [Use the ObjectMapper](#use-the-objectmapper).
- The WADL description is switched off.
- `services.jersey.server.config.mediaTypes.<suffix>` maps a path suffix to a media type. Core hands the mappings
  to Jersey as `ServerProperties.MEDIA_TYPE_MAPPINGS`. The defaults are in the
  [reference](#configuration-keys).
- There are no HTTP sessions.

## Add a servlet

A servlet is known by a name. Its path is read from the configuration under `servlets.<name>`.

Hand in an instance and declare its path:

```java
import io.descoped.stride.application.config.Servlet;
import io.descoped.stride.application.config.Servlets;

Components components = Components.builder()
        .servlet("hello", new HelloServlet("hi there"))
        .build();

ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
        .defaults()
        .servlets(Servlets.builder().servlet(Servlet.builder("hello").pathSpec("/hello")));
```

An instance is always registered. `servlets.<name>.enabled=false` does not remove it; Stride logs a warning.

Or name the class and let Stride create it through HK2:

```java
.servlets(Servlets.builder().servlet(Servlet.builder("stock").clazz(StockServlet.class).pathSpec("/stock")))
```

A servlet needs a path, as an instance or as a class. Without `servlets.<name>.config.pathSpec` the application
does not start: `Servlet 'stock' has no path: set servlets.stock.config.pathSpec`.

Jersey is mapped to `/*`. A servlet with a more specific path takes the requests for that path.

Servlets belong to the server, not to Jersey: the service `jetty.server` registers them, after the filters and
`/ping`. With `services.jersey.server.enabled=false` there are no REST resources. Servlets, servlet context
initializers, filters and `/ping` remain.

### Give a servlet a service through the servlet context

A servlet that Stride did not wire can read a collaborator from a servlet context attribute. `binding` puts a
service that is declared in the configuration into an attribute. `validate` makes the application refuse to start
when a required attribute or named service is missing.

```java
import io.descoped.stride.application.config.ServletContextBinding;
import io.descoped.stride.application.config.ServletContextValidation;

ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder().defaults()
        .services(Services.builder().service(Service.builder("stockService").clazz(Stock.class).runLevel(5)))
        .servlets(Servlets.builder().servlet(Servlet.builder("stock")
                .clazz(StockServlet.class)
                .pathSpec("/stock")
                .binding(ServletContextBinding.builder().bind("stock", "stockService"))
                .validate(ServletContextValidation.builder().requireAttribute("stock"))));
```

```java
public class StockServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Stock stock = (Stock) getServletContext().getAttribute("stock");
        response.getWriter().print(stock.of(request.getParameter("item")));
    }
}
```

`bind(attribute, serviceName)` takes the name the service has in the configuration. A name that is not a service
with a class in the configuration binds nothing and is not reported: instances from `Components` and discovered
`@Service` classes cannot be bound this way. Require the attribute to catch a wrong name.

A servlet's `validate` checks both kinds of requirement, once the servlet's bindings are set:

- `requireAttribute(name)`: a servlet context attribute. A missing one stops the start with
  `Required servletContext attribute 'stock' for 'stock' servlet IS NOT set!`.
- `requireNamedService(type, name)`: a service of that type and name, for example one a
  [servlet context initializer](#prepare-the-servlet-context) added. A missing one stops the start with
  `Required service 'com.example.Stock' named 'main' for 'stock' servlet is NOT available!`.

## Prepare the servlet context

A servlet context initializer runs once at run level 10, before servlets and resources are registered. Use it to
set servlet context attributes or to offer named services that a library's servlets and features expect.

```java
import io.descoped.stride.application.core.ServletContextInitializer;
import jakarta.servlet.ServletContext;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

public class Announcer implements ServletContextInitializer {
    @Override
    public void initialize(ServiceLocator locator, ServletContext context) {
        context.setAttribute("announced", "yes");
        ServiceLocatorUtilities.addOneConstant(locator, "ready", "announcement", String.class);
    }
}
```

```java
import io.descoped.stride.application.config.ServletContextInitialization;

ApplicationConfiguration.builder().defaults()
        .initialization(ServletContextInitialization.builder()
                .initializerClass(Announcer.class)
                .validate(ServletContextValidation.builder()
                        .requireAttribute("announced")
                        .requireNamedService(String.class, "announcement")));
```

- Stride creates the initializer through HK2, so it may have `@Inject` members.
- Initializers run in the order they are declared.
- A named service an initializer adds to the `locator` can be a
  [constructor argument of a configured resource](#a-class-with-constructor-arguments).
- The requirements are checked once every initializer has run, so one initializer may provide what another does
  not. A missing attribute or named service stops the application from starting, with a message that names it:
  `Required service 'java.lang.String' named 'announcement' for the servlet context initialization is NOT
  available!`.
- Initializers belong to the server, as servlets do. They run whether or not the Jersey service is enabled.

## Declare a filter

A filter is a `jakarta.servlet.Filter`. Like a servlet it is known by a name, and its settings are read from the
configuration under `filters.<name>`. Filters belong to the server, not to Jersey: they apply to REST resources,
to every servlet and to `/ping`.

Hand in an instance:

```java
Components components = Components.builder()
        .filter("audit", new AuditFilter(auditLog))
        .build();
```

Without a configuration entry it applies to `/*`, to the dispatcher type `REQUEST`, at order 1000. Declare the
name with its builder to set these, and to make them overridable by environment variable and system property:

```java
import io.descoped.stride.application.config.Filter;
import io.descoped.stride.application.config.Filters;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;

ApplicationConfiguration.builder()
        .defaults()
        .filters(Filters.builder()
                .filter(Filter.builder("audit")
                        .pathSpec("/orders/*")
                        .order(500)
                        .dispatches(EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD))));
```

Or name the class and let Stride create it through HK2, which injects its `@Inject` constructor:

```java
.filters(Filters.builder().filter(Filter.builder("audit").clazz(AuditFilter.class).order(500)))
```

Rules:

- An instance and a configuration entry of the same name belong together: the instance is used, with the
  settings of the entry.
- An instance is always registered. `filters.<name>.enabled=false` does not remove it; Stride logs a warning.
  If you want a switch, read your own configuration and leave the instance out.
- An enabled entry with neither a class nor an instance stops the application from starting. The message names
  `filters.<name>.config.class`.
- Two instances cannot have the same name: `Components.Builder.filter` throws `IllegalArgumentException`.

### Filter order

Filters run in ascending `order`, then by name. This is the only ordering rule.

| Order | Filter |
|---|---|
| 100 | CORS (`filters.cors`) |
| 200 | Authentication (`filters.security`, from [stride-application-security](security.md)) |
| 1000 | A filter without an order (`Filter.DEFAULT_ORDER`) |

Set `filters.<name>.config.order`, or `.order(n)` on the builder, to place yours. A filter that must see every
request, including those CORS answers itself, needs an order below 100. An order that is not a whole number
stops the application from starting.

## Configure CORS

Core registers the CORS filter `ApplicationCORSServletFilter` under the name `cors`, on `/*`, at order 100. It is
on by default and allows every origin.

| Request | Response |
|---|---|
| No `Origin` header | Passed on unchanged. It is not a CORS request. |
| `Origin` that is not allowed | 403 with an empty body. The request does not reach the application. |
| `Origin` that is allowed | Passed on, with `Access-Control-Allow-Origin` and `Vary: Origin`. |
| Preflight: `OPTIONS` with `Access-Control-Request-Method` | 204 with the allowed methods, headers and max age. 403 when the requested method or a requested header is not allowed. Later filters and the application do not see it. |

`Access-Control-Allow-Origin` is `*` when every origin is allowed, otherwise the origin of the request.

Restrict the origins with a comma separated list. An origin must match one entry exactly:

```properties
filters.cors.config.origin=https://shop.example.com, https://admin.example.com
```

`filters.cors.config.credentials=true` adds `Access-Control-Allow-Credentials: true`. It cannot be combined with
the origin `*`: the application then does not start.

`filters.cors.enabled=false` removes the filter. All keys are in the [reference](#configuration-keys).

To build the filter yourself, use its builder and hand the instance in under the name `cors`. It replaces the
configured class and keeps the order and path of `filters.cors`:

```java
import io.descoped.stride.application.cors.ApplicationCORSServletFilter;

Components components = Components.builder()
        .filter("cors", ApplicationCORSServletFilter.builder()
                .origin("https://shop.example.com")
                .credentials("true")
                .build())
        .build();
```

A filter built this way does not read `filters.cors.config.origin`, `credentials`, `headers`, `methods` or
`max-age`: set them with `origin(..)`, `credentials(..)`, `headers(..)`, `methods(..)` and `maxAge(..)`, which
all take a string. The builder starts from the same values as the configuration: its methods are
`GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD`. `build()` throws an `IllegalArgumentException` when credentials
are combined with the origin `*`.

## Run behind a proxy

- `services.jetty.server.config.context-path` (default `/`) is the path every route is under, `/ping` included.
  With `/api`, the resource `/greetings` is served at `/api/greetings` and `/ping` answers 404.
- `services.jetty.server.config.forwarded.enabled` (default `false`) makes the application see the address the
  proxy forwards: with `X-Forwarded-Proto: https` and `X-Forwarded-Host: api.example.com`, `UriInfo.getBaseUri()`
  is `https://api.example.com/`. Core adds Jetty's `ForwardedRequestCustomizer` for this. Enable it only behind a
  proxy that sets or removes these headers itself; a client could otherwise forge them.
- Core opens one clear-text listener. It serves HTTP/1.1 and, with
  `services.jetty.server.config.http2.enabled` (default `true`), clear-text HTTP/2 (h2c). Core has no TLS
  listener: terminate TLS in front of the application.
- `services.jetty.server.config.host` (default `localhost`) is the address to listen on. Change it when the proxy
  runs on another host.

## Check that the application is up

`GET /ping` answers 200 with an empty body on every Stride application. It is inside the context path. Filters
apply to it, so with authentication enabled it needs a token unless it is a public path
(see [security.md](security.md)).

## Threads

- Every request runs on a new virtual thread named `stride-http-N`. Blocking code is fine.
- Nothing limits the number of concurrent requests. If you need a ceiling, add a filter with a low order that
  admits a bounded number of requests.
- `services.jetty.server.config.minThreads` and `maxThreads` size the platform threads that accept connections
  and select I/O. Requests do not use them, so they do not limit concurrent requests.
- Run level services start on virtual threads named `stride-runlevel-N`.
- Threads are never pooled or reused. Do not keep per-request state in a `ThreadLocal` expecting reuse; use a
  `ScopedValue`.
- `VirtualThreads.perTaskExecutor(namePrefix)` (package `io.descoped.stride.application.server`) gives your own
  code the same kind of executor: a new named virtual thread per task, with uncaught exceptions logged.

## Stop gracefully

`close()` and `stop()` do the same:

1. Services stop in the reverse order of their run levels, so those above 11 stop first. A service that
   implements `org.glassfish.hk2.api.PreDestroy` is told.
2. At run level 11 the server stops accepting requests. Requests that are running get
   `services.jetty.server.config.stopTimeout` (default `PT30S`) to finish.
3. The web server at run level 10 and the application's services below it stop.

Register `close()` as a shutdown hook, as the `main` method above does, so that a `SIGTERM` stops the application
this way. `StrideApplication` is `AutoCloseable` and can be used in a try-with-resources statement.

Durations are written in the ISO-8601 form of `java.time.Duration`: `PT30S` is 30 seconds.

## Survive a burst of connections

`services.jetty.server.config.acceptQueueSize` (default `1024`) is how many new connections may wait to be
accepted. A connection that arrives when the queue is full is refused or reset. The operating system caps the
number. With 0 or less the listener gets the Java default of 50.

## Logging

- Core logs through SLF4J. `java.util.logging` is routed to SLF4J as well.
- Each request is logged through the SLF4J logger named `jetty`.
- `logging.verbose=true` adds debug lines about which services are enabled and which run levels are reached. The
  loggers must also be at debug level.

## What core does not bring

Core contains what every application needs. The rest is yours to add:

| Not in core | What to do |
|---|---|
| A logging backend | Add `slf4j-simple`, Logback or another SLF4J backend. |
| Authentication | Add [stride-application-security](security.md). |
| OpenAPI | Add [stride-application-openapi](openapi.md). |
| Metrics | Add the library you use. Register its servlets and features as described above. |
| WebSocket | Declare the library you use. |
| XML (JAXB) | Declare it if your resources produce or consume XML. |
| TLS | Terminate it in front of the application. |
| Persistence, messaging, HTTP clients, validation | Your choice of library. Wire them as services. |

To test an application, see [testing.md](testing.md).

## Java module

The module name is `stride.application.core`. It exports:

| Package | For the application |
|---|---|
| `io.descoped.stride.application.core` | `ServletContextInitializer`; `StrideApplicationImpl` with its `main` method. |
| `io.descoped.stride.application.server` | `RunLevelConstants`, `VirtualThreads`; the services `jetty.server`, `jersey.server`, `jetty.start` and `jersey.objectMapper`. |
| `io.descoped.stride.application.cors` | `ApplicationCORSServletFilter` and its builder. |

It provides `io.descoped.stride.application.spi.ApplicationInitializer`, which is how `StrideApplication.create`
finds it. Its `requires` are not transitive: a module that uses `StrideApplication`, `Components` or
`ApplicationConfiguration` requires `stride.application.api`, which passes on `stride.application.config`.

The sample application has no module descriptor and runs on the class path.

## Reference

### Configuration keys

Core has no defaults file of its own. The keys it reads are declared in the
`META-INF/stride/defaults.properties` of `stride-application-config`. How to set a key is in
[config.md](config.md).

Server:

| Key | Default | Meaning |
|---|---|---|
| `services.jetty.server.config.host` | `localhost` | The address to listen on. |
| `services.jetty.server.config.port` | `0` | The port. 0 lets the operating system pick a free one. |
| `services.jetty.server.config.context-path` | `/` | The path every route is under. |
| `services.jetty.server.config.http2.enabled` | `true` | Clear-text HTTP/2 (h2c) next to HTTP/1.1. |
| `services.jetty.server.config.forwarded.enabled` | `false` | Trust the headers a proxy forwards. |
| `services.jetty.server.config.stopTimeout` | `PT30S` | How long running requests get to finish at shutdown. |
| `services.jetty.server.config.acceptQueueSize` | `1024` | How many new connections may wait to be accepted. |
| `services.jetty.server.config.idleTimeout` | `PT30S` | How long an idle connection is kept. |
| `services.jetty.server.config.minThreads` | `10` | Fewest platform threads for accepting and selecting. |
| `services.jetty.server.config.maxThreads` | `150` | Most platform threads for accepting and selecting. |
| `services.jetty.server.config.outputBufferSize` | `32768` | Response buffer size in bytes. |
| `services.jetty.server.config.requestHeaderSize` | `16384` | Largest request header in bytes. |

Services:

| Key | Default | Meaning |
|---|---|---|
| `services.jetty.server.enabled` | `true` | The service that assembles the server: filters, `/ping`, servlet context initializers and servlets. |
| `services.jetty.start.enabled` | `true` | The service that opens the listener at run level 11. |
| `services.jersey.server.enabled` | `true` | The service that registers REST resources with Jersey. |
| `services.jersey.objectMapper.enabled` | `true` | Core's `ObjectMapper`, for injection and for the JSON of resources. |
| `services.hk2.defaults.enabled` | `true` | Whether a discovered named service without its own `services.<name>.enabled` key is on. |
| `services.hk2.threadcount` | `20` | How many services of one run level may start at the same time. |
| `services.hk2.threadpolicy` | `FULLY_THREADED` | The HK2 run level threading policy. |
| `services.hk2.SystemInjectResolver.enabled` | `true` | Declared for the HK2 service of that name. Leave it on. |
| `services.hk2.HK2TopicDistributionService.enabled` | `true` | Declared for the HK2 service of that name. Leave it on. |
| `services.hk2.DefaultRunLevelContext.enabled` | `true` | Switch of HK2's run level context, a service of that name. Run levels need it: leave it on. |
| `logging.verbose` | `false` | Debug lines about services and run levels. |

Jersey media type mappings:

| Key | Default |
|---|---|
| `services.jersey.server.config.mediaTypes.json` | `application/json` |
| `services.jersey.server.config.mediaTypes.yaml` | `application/yaml` |
| `services.jersey.server.config.mediaTypes.txt` | `text/plain` |
| `services.jersey.server.config.mediaTypes.html` | `text/html` |
| `services.jersey.server.config.mediaTypes.schema` | `application/schema+json` |

CORS:

| Key | Default | Meaning |
|---|---|---|
| `filters.cors.enabled` | `true` | Whether the CORS filter is registered. |
| `filters.cors.config.class` | `io.descoped.stride.application.cors.ApplicationCORSServletFilter` | The filter class. |
| `filters.cors.config.pathSpec` | `/*` | The requests it applies to. |
| `filters.cors.config.order` | `100` | Its place among the filters. |
| `filters.cors.config.dispatches.0` | `FORWARD` | A dispatcher type it applies to. |
| `filters.cors.config.dispatches.1` | `REQUEST` | A dispatcher type it applies to. |
| `filters.cors.config.origin` | `*` | Allowed origins, comma separated, or `*` for all. |
| `filters.cors.config.credentials` | `false` | Whether to send `Access-Control-Allow-Credentials: true`. |
| `filters.cors.config.headers` | `origin, content-type, accept, authorization` | Request headers a preflight may ask for. |
| `filters.cors.config.methods` | `GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD` | Methods a preflight may ask for. |
| `filters.cors.config.max-age` | `86400` | Seconds a browser may cache a preflight answer. |

### Keys per component

The builders write these keys. A properties file can set them too. A builder sets `enabled` to `true`; a
component written only in a properties file is off until its `enabled` key is `true`.

| Component | Keys |
|---|---|
| Service | `services.<name>.enabled`, `services.<name>.config.class`, `services.<name>.config.runLevel` |
| Resource | `resources.<name>.enabled`, `resources.<name>.config.class` |
| Servlet | `servlets.<name>.enabled`, `servlets.<name>.config.class`, `servlets.<name>.config.pathSpec` |
| Filter | `filters.<name>.enabled`, `filters.<name>.config.class`, `filters.<name>.config.pathSpec`, `filters.<name>.config.order`, `filters.<name>.config.dispatches.<n>` |

Filter defaults: path `/*` (`Filter.DEFAULT_PATH_SPEC`), order 1000 (`Filter.DEFAULT_ORDER`), dispatcher type
`REQUEST`.

### Thread names

| Name | Kind | Used for |
|---|---|---|
| `stride-http-N` | virtual | One per request. |
| `stride-runlevel-N` | virtual | One per starting service. |
| `stride-jetty-…` | platform | Accepting connections and selecting I/O. |
