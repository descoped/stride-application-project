# Configuration: stride-application-config

`stride-application-config` holds an application's configuration. It reads keys and values from layered sources,
and it has builders that declare the services, filters, servlets and REST resources Stride should create. Every
Stride application uses it: `ApplicationConfiguration` is the first argument to `StrideApplication.create(..)`
(see [api.md](api.md)). The module comes with `stride-application-api` and `stride-application-core`, so you only
add it yourself in a library that reads configuration without depending on those.

```xml
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-config</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The Java module is `stride.application.config`. It exports `io.descoped.stride.application.config` (the
configuration and the builders) and `io.descoped.stride.application.jackson` (`JsonElement`). It also exports two
helper packages that Stride uses itself: `io.descoped.stride.application.config.exception` (`ExceptionFunction`)
and `io.descoped.stride.application.config.utils` (`ClassPathResourceUtils`). It requires
`io.descoped.dynamic.config` transitively, so `DynamicConfiguration.Builder` is readable too.

## Build a configuration

```java
import io.descoped.stride.application.config.ApplicationConfiguration;

ApplicationConfiguration configuration = ApplicationConfiguration.builder()
        .defaults()
        .build();
```

`ApplicationConfiguration` is immutable. Every source is read once, in `build()`.

The builder methods that choose where values come from:

| Method | What it adds |
|---|---|
| none | Only the module defaults, the components declared in code and `overrideProperty`. Environment variables and system properties are not read. |
| `defaults()` | `application.properties` from the class path, then `local_override.properties` from the working directory, then environment variables, then system properties. |
| `testDefaults()` | As `defaults()`, with `test_override.properties` from the class path in place of `local_override.properties`. |
| `configuration(sources -> ..)` | Your own sources, in place of `defaults()` and `testDefaults()`. The argument is a `Consumer<DynamicConfiguration.Builder>`. |
| `overrideProperty(key, value)` | One value that wins over every source. Mostly for tests. |
| `classLoader(loader)` | The class loader used to find the module defaults. The default is the thread's context class loader, or the one that loaded Stride when there is none. |

A file that does not exist is skipped. Only the first `application.properties` on the class path is read, so it
belongs to the application; a library declares its keys in a defaults file instead (see
[Declare your own keys](#declare-your-own-keys)).

Profiles come with the configuration library. With `-Dconfig.profiles=test` or `CONFIG_PROFILES=test`, each
properties file is followed by its sibling for that profile when it exists: `application-test.properties` after
`application.properties`.

With `configuration(..)` you decide the sources and their order. Later sources win. Add `environment()` and
`systemProperties()` yourself if you want them:

```java
ApplicationConfiguration configuration = ApplicationConfiguration.builder()
        .configuration(sources -> sources
                .value("services.jetty.server.config.host", "0.0.0.0")
                .systemProperties())
        .build();
```

`DynamicConfiguration.Builder` (package `io.descoped.config`) also has `classpath(name)`, `resource(url)`,
`file(path)`, `requiredFile(path)`, `directory(path)`, `values(map)`, `source(..)`, `environment(prefix)` and
`systemProperties(prefix)`.

In a test, start from the application's own builder and override what the test needs:

```java
GreetingApplication.configuration().overrideProperty("greeting.salutation", "Hei")
```

See [testing.md](testing.md) for how a test hands the builder to Stride, and [sample.md](sample.md) for the
application this line comes from.

## Layers and precedence

Configuration is a flat set of keys. The layers, lowest first:

1. The `META-INF/stride/defaults.properties` of every module on the class path. These files declare every key and
   its default.
2. Components declared in code with the builders (services, filters, servlets, resources, initializers).
3. The application's sources. With `defaults()`: `application.properties`, then `local_override.properties`, then
   environment variables, then system properties.
4. `overrideProperty(key, value)`.

A higher layer wins, key by key. Code decides which components exist. Whoever deploys the application can still
change their values.

Two rules keep it predictable:

- **Every key is declared.** Environment variables and system properties can only override a key that another
  source holds: a defaults file, a builder, a properties file or `overrideProperty`. They never add a key, so a
  misspelled variable has no effect.
- **No defaults in code.** Read keys with `requireString`, `requireInt` or `requireBoolean`.

A value can refer to another key with `${other.key}`, or with `${other.key:fallback}` to give a value for when
that key does not exist. The security module's defaults do this: `filters.security.enabled=${security.enabled}`.
A reference to a key that does not exist, without a fallback, fails when the configuration is built. Write `$${`
for a literal `${`.

## Declare your own keys

Declare your application's keys, with their defaults, in your own `META-INF/stride/defaults.properties`
(`src/main/resources/META-INF/stride/defaults.properties`):

```properties
greeting.salutation=Hello
```

Now `GREETING_SALUTATION=Howdy` or `-Dgreeting.salutation=Howdy` overrides it. A library can ship such a file
too.

- A key declared by two modules is an error when the configuration is built. The message names the key and both
  files.
- If you build a single jar, append the files of this name; do not let one replace the others. With the Maven
  Shade plugin that is an `AppendingTransformer` for `META-INF/stride/defaults.properties`.
- A key cannot be both a value and the parent of other keys. `a.b=1` together with `a.b.c=2` fails.

Put the values your application sets differently in `application.properties`:

```properties
application.alias=greetings
services.jetty.server.config.port=8080
```

## Override a key from the environment

The environment variable for a key is the key in upper case, with each character that is not a letter or a digit
replaced by `_`:

| Key | Environment variable | System property |
|---|---|---|
| `services.jetty.server.config.port` | `SERVICES_JETTY_SERVER_CONFIG_PORT` | `-Dservices.jetty.server.config.port=8080` |
| `services.jetty.server.config.context-path` | `SERVICES_JETTY_SERVER_CONFIG_CONTEXT_PATH` | `-Dservices.jetty.server.config.context-path=/api` |
| `greeting.salutation` | `GREETING_SALUTATION` | `-Dgreeting.salutation=Howdy` |

Environment variables and system properties are read only with `defaults()`, `testDefaults()`, or a
`configuration(..)` that adds `environment()` or `systemProperties()`.

Two keys that give the same variable name, such as `a.b-c` and `a.b.c`, cannot both be set from it: when that
variable is set, building the configuration fails.

## Read a value

`ApplicationConfiguration` is a `JsonElement`, so it has the read methods directly. A key is a dotted path.

```java
String salutation = configuration.requireString("greeting.salutation");
int port = configuration.requireInt("services.jetty.server.config.port");
boolean http2 = configuration.requireBoolean("services.jetty.server.config.http2.enabled");
```

| Method | Returns | Fails with `IllegalStateException` when |
|---|---|---|
| `requireString(key)` | the text | the key is missing |
| `requireInt(key)` | the number | the key is missing or the value is not a whole number |
| `requireBoolean(key)` | `true` for the text `true` in any letter case, `false` for any other text | see below |

The message names the key as it was passed: `Configuration key 'greeting.salutation' is missing or malformed; it
must be declared in the META-INF/stride/defaults.properties of the module that reads it`. After `with(..)`, and
in the typed accessors below, that is the part of the key after the prefix.

`requireBoolean` on an `ApplicationConfiguration` does not fail for a missing key: it returns `false`. Declare
the key, and check its spelling.

A key declared with an empty value (`application.url=`) is present: `requireString` returns `""`.

To read several keys under one prefix, navigate first:

```java
JsonElement greeting = configuration.with("greeting");
String salutation = greeting.requireString("salutation");
```

Stride's own settings have typed accessors:

| Accessor | Reads |
|---|---|
| `configuration.server()` | `services.jetty.server.config.*`: `host()`, `port()`, `contextPath()`, `minThreads()`, `maxThreads()`, `outputBufferSize()`, `requestHeaderSize()`, `isHttp2Enabled()`, `idleTimeout()`, `acceptQueueSize()`, `stopTimeout()`. The two timeouts are returned in milliseconds. |
| `configuration.application()` | `application.*`: `alias()`, `version()`, and `url()` as an `Optional<String>` that is empty when the key is blank. |
| `configuration.jersey()` | `mediaTypes()`: the entries of `services.jersey.server.config.mediaTypes` as `extension:media/type`. |
| `configuration.isVerboseLogging()` | `logging.verbose` |
| `configuration.services()`, `filters()`, `servlets()`, `resources()`, `initialization()` | The declared components, see below. |

`configuration.toPrettyString()` returns the whole configuration as indented JSON. Do not log it if it holds
secrets.

## Declare components

A component is something Stride creates and registers for you: a service, a servlet filter, a servlet or a REST
resource. You name its class in the configuration, and Stride creates it when the application starts. Each
component has a name, and its settings live under `<kind>.<name>`, for example `filters.audit.config.order`. A name
may contain dots (`jetty.server`).

You do not have to use this. An application can build its own objects and hand them to Stride with `Components`;
see [api.md](api.md). The two combine: an instance handed in under a name takes the place of the class, and the
configuration entry of the same name supplies its settings.

Declare components in code with the builders:

```java
ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
        .defaults()
        .services(Services.builder()
                .service(Service.builder("orders").clazz(OrderService.class).runLevel(5)))
        .filters(Filters.builder()
                .filter(Filter.builder("audit").clazz(AuditFilter.class).order(500)))
        .servlets(Servlets.builder()
                .servlet(Servlet.builder("admin").clazz(AdminServlet.class).pathSpec("/admin/*")))
        .resources(Resources.builder()
                .resource(Resource.builder("orders").clazz(OrderResource.class)));
```

Rules that hold for every kind:

- A component declared with a builder is enabled unless you call `enabled(false)`.
- What a builder writes becomes ordinary keys in layer 2. Those keys are declared, so a higher layer can override
  them: `-Dservices.orders.config.runLevel=15`, `SERVICES_ORDERS_ENABLED=false`. A setting the builder did not
  write is not declared and cannot be set from the environment. Filters are the exception: `pathSpec`, `order`
  and the first dispatcher type are always declared for a filter made with `Filter.builder`.
- Calling `services(..)`, `filters(..)`, `servlets(..)` or `resources(..)` again replaces the earlier call. Put
  all components of one kind in one builder.
- The same keys can be written in a properties file instead. There, a component without `enabled=true` is
  disabled.

```properties
filters.audit.enabled=true
filters.audit.config.class=com.example.AuditFilter
filters.audit.config.order=500
```

### Declare a service

A service is an object Stride creates at a run level during start, and stops in reverse order at stop. Run
levels are explained in [core.md](core.md).

```java
Services.builder()
        .service(Service.builder("orders")
                .clazz(OrderService.class)
                .runLevel(5)
                .metadata(Metadata.builder().property("region", "eu")))
```

| Builder method | Key | Meaning |
|---|---|---|
| `enabled(boolean)` | `services.<name>.enabled` | Whether Stride creates the service. |
| `clazz(Class)`, `className(String)` | `services.<name>.config.class` | The class to create. |
| `runLevel(int)` | `services.<name>.config.runLevel` | The run level it starts at. Always set it for a class you name. |
| `metadata(Metadata.Builder)` | `services.<name>.metadata.<property>` | Free-form properties attached to the service. |

A class named here must not carry a scope annotation or HK2's `@Service`; Stride refuses to start and says so.

An entry without a class configures a service that HK2 discovers by itself under that name. This is how Stride's
own services are switched: `services.jetty.server.enabled`, `services.jersey.server.enabled`. A named service
that HK2 discovers is disabled unless `services.<name>.enabled` is `true`, so declare that key for every such
service of your own. A service without a name is always enabled.

`Service.builder(Class)` uses the class name as the service name. It does not set the class; call `clazz(..)` as
well.

### Declare a filter

A filter is a `jakarta.servlet.Filter`. Stride creates the class, or uses the instance you handed in under the
same name.

```java
Filters.builder()
        .filter(Filter.builder("audit")
                .clazz(AuditFilter.class)
                .pathSpec("/api/*")
                .order(500)
                .dispatches(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC)))
```

| Builder method | Key | Default | Meaning |
|---|---|---|---|
| `enabled(boolean)` | `filters.<name>.enabled` | `true` with a builder | Whether the filter is registered. |
| `clazz(Class)`, `className(String)` | `filters.<name>.config.class` | none | The filter class. Not needed when an instance is handed in. |
| `pathSpec(String)` | `filters.<name>.config.pathSpec` | `/*` (`Filter.DEFAULT_PATH_SPEC`) | The paths the filter applies to. |
| `order(int)` | `filters.<name>.config.order` | `1000` (`Filter.DEFAULT_ORDER`) | The position in the chain. |
| `dispatches(EnumSet<DispatcherType>)` | `filters.<name>.config.dispatches.<index>` | `REQUEST` | The dispatcher types the filter applies to. |

Filters run in ascending `order`, then by name. Stride's CORS filter is at 100 and the security module's
authentication filter at 200 (see [security.md](security.md)). A filter without an order is at 1000. An `order`
that is not a whole number fails at startup.

To make the settings of a filter instance overridable from the environment, declare it by name without a class:

```java
Components components = Components.builder()
        .filter("audit", new AuditFilter(auditLog))
        .build();

ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
        .defaults()
        .filters(Filters.builder().filter(Filter.builder("audit").order(500)));
```

Now `-Dfilters.audit.config.pathSpec=/api/*` applies. An instance without a configuration entry applies to `/*`
for `REQUEST` at order 1000. An instance is always registered:
`filters.audit.enabled=false` does not remove it, and Stride logs a warning. An enabled filter with neither a
class nor an instance fails at startup.

Declaring `Filter.builder("cors")` changes Stride's CORS filter. Only the settings you set change; the rest keep
their defaults.

`Filter.Builder.context(..)` stores a value under `filters.<name>.config.context`. Stride does not act on it.

### Declare a servlet

```java
Servlets.builder()
        .servlet(Servlet.builder("stock")
                .clazz(StockServlet.class)
                .pathSpec("/stock")
                .binding(ServletContextBinding.builder().bind("stock", "stockService"))
                .validate(ServletContextValidation.builder().requireAttribute("stock")))
```

| Builder method | Key | Meaning |
|---|---|---|
| `enabled(boolean)` | `servlets.<name>.enabled` | Whether the servlet is registered. |
| `clazz(Class)`, `className(String)` | `servlets.<name>.config.class` | The servlet class. Not needed when an instance is handed in. |
| `pathSpec(String)` | `servlets.<name>.config.pathSpec` | The path the servlet is mapped to. Required. |
| `binding(ServletContextBinding.Builder)` | `servlets.<name>.config.binding.<attribute>` | Services to put in the servlet context, see below. |
| `validate(ServletContextValidation.Builder)` | `servlets.<name>.config.validation.requires.*` | What must be in the servlet context, see below. |

A servlet instance handed in with `Components` needs a configuration entry of the same name with a `pathSpec`:

```java
Servlets.builder().servlet(Servlet.builder("hello").pathSpec("/hello"))
```

Without it the application does not start: `Servlet 'hello' has no path: set servlets.hello.config.pathSpec`.
A servlet instance is always registered: `servlets.hello.enabled=false` does not remove it.

### Give a servlet its collaborators: ServletContextBinding

A servlet created from its class often reads collaborators from the servlet context. `ServletContextBinding`
puts a declared service there before the servlet is registered.

`bind(attributeName, serviceName)` takes the name of a service declared under `services` that has a class.
Stride gets that service and sets it as the servlet context attribute `attributeName`.

```java
ApplicationConfiguration.builder().testDefaults()
        .services(Services.builder()
                .service(Service.builder("stockService").clazz(Stock.class).runLevel(5)))
        .servlets(Servlets.builder()
                .servlet(Servlet.builder("stock")
                        .clazz(StockServlet.class)
                        .pathSpec("/stock")
                        .binding(ServletContextBinding.builder().bind("stock", "stockService"))));
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

A binding whose service name is not declared, or is declared without a class, sets nothing and does not fail.
Add a `ServletContextValidation` to fail at startup instead.

`bind(attributeName, Class)` is the same with the class name as the service name. It finds a service declared
with `Service.builder(Class)`.

### Fail early on a missing collaborator: ServletContextValidation

`ServletContextValidation` lists what must be present. If something is missing, the application does not start.

| Builder method | Requires |
|---|---|
| `requireAttribute(name)` | A servlet context attribute of that name. |
| `requireAttributes(Set<String>)` | Each of those attributes. Replaces the attributes required so far. |
| `requireNamedService(type, name)` | A service of that type and name. Checked for the initialization only, not for a servlet. |

On a servlet (`Servlet.Builder.validate(..)`) the attributes are checked after the servlet's bindings are set.
A missing one fails with `Required servletContext attribute 'stock' for 'stock' servlet IS NOT set!`.

### Prepare the servlet context: ServletContextInitialization

An initializer is a class that runs once, before servlets and resources are registered. It can set servlet
context attributes and add named services. It implements `ServletContextInitializer` from
`stride-application-core` (see [core.md](core.md)):

```java
public class Announcer implements ServletContextInitializer {
    @Override
    public void initialize(ServiceLocator locator, ServletContext context) {
        context.setAttribute("announced", "yes");
        ServiceLocatorUtilities.addOneConstant(locator, "ready", "announcement", String.class);
    }
}
```

```java
ApplicationConfiguration.builder().testDefaults()
        .initialization(ServletContextInitialization.builder()
                .initializerClass(Announcer.class)
                .validate(ServletContextValidation.builder()
                        .requireAttribute("announced")
                        .requireNamedService(String.class, "announcement")));
```

Initializers run in the order they are added. The requirements are checked once, after all of them have run, so
one initializer may provide what another does not. The keys are `initializers.config.classes.<index>` and
`initializers.config.validation.requires.*`.

### Declare a resource

A resource is a JAX-RS class, or any class Jersey accepts for registration, such as a `Feature`.

```java
Resources.builder()
        .resource(Resource.builder("orders").clazz(OrderResource.class))
```

| Builder method | Key | Meaning |
|---|---|---|
| `enabled(boolean)` | `resources.<name>.enabled` | Whether the resource is registered. |
| `clazz(Class)`, `className(String)` | `resources.<name>.config.class` | The resource class. |
| `args(Args.Builder)` | `resources.<name>.config.args.<index>.class` and `.named` | Constructor arguments, see below. |

Without `args`, Jersey creates the class, and injection is available to it.

### Pass constructor arguments to a resource: Args

With `args`, Stride creates the resource itself. Each argument is a service, looked up by type and name, and
passed to the constructor that takes exactly those types in that order. The class needs no injection annotations.

```java
@Path("/vat")
public class VatResource {
    private final Rates rates;

    public VatResource(Rates rates) {
        this.rates = rates;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String vat() {
        return rates.percent() + "%";
    }
}
```

```java
Components components = Components.builder()
        .service(Rates.class, "norway", new Rates(25))
        .build();

ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder().testDefaults()
        .resources(Resources.builder().resource(Resource.builder("vat")
                .clazz(VatResource.class)
                .args(Args.builder().arg(Rates.class, "norway"))));
```

The application does not start when an argument has no service, when no constructor takes those types, or when
the constructor throws. The message names the resource and the cause.

If you already have the object, it is simpler to hand it over: `Components.builder().resource(new
VatResource(rates))`.

### Lists in keys

A list is written as indexed keys:

```properties
filters.cors.config.dispatches.0=FORWARD
filters.cors.config.dispatches.1=REQUEST
```

A higher layer replaces a list element by element, not as a whole. An element with an empty value is left out,
which is how a higher layer shortens a list.

### Look up declared components

```java
Filter cors = configuration.filters().filter("cors").orElseThrow();
int order = cors.order();                          // 100
EnumSet<DispatcherType> dispatches = cors.dispatches();

for (Service service : configuration.services().iterator()) {
    // service.name(), service.isEnabled(), service.className(), service.runLevel()
}
```

Each collection has a lookup by name, a lookup by class name and `iterator()`. The lookup by name does not tell
whether a component is declared: for a name that is not declared it returns a component that is not enabled and
has no class. The lookup by class name and `iterator()` return declared components only.

| Collection | By name | By class name |
|---|---|---|
| `Services` | `service(name)` | `serviceByClass(className)` |
| `Filters` | `filter(name)` | `filterByClass(className)` |
| `Servlets` | `servlet(name)` | `servletByClass(className)` |
| `Resources` | `resource(name)` | `resourceByClass(className)` |

## JsonElement

`JsonElement` (package `io.descoped.stride.application.jackson`) is a small wrapper around a Jackson `JsonNode`.
The configuration is held as a JSON tree: a key is split on `.`, a node whose children are all indexes becomes an
array, and every value is text. `JsonElement` navigates that tree and converts values.

Create one from any `JsonNode`:

| Factory | Navigating to a node that does not exist |
|---|---|
| `JsonElement.ofStrict(node)` | gives an empty element; reads return the default or fail. |
| `JsonElement.ofEphemeral(node)` | gives a temporary empty node, so chained navigation never fails. `ApplicationConfiguration` works this way. |
| `JsonElement.ofDynamic(node)` | creates the node in the tree. For building a tree, not for reading. |

Navigate:

| Method | Meaning |
|---|---|
| `with("a.b")` | The child at a dotted path. |
| `at(index)` | The element of an array at that position. |
| `get(name)` | The direct child as an `Optional<JsonElement>`. |
| `has(name)` | Whether a direct child exists. |
| `isEmpty()` | Whether the element holds no node. |
| `json()` | The underlying `JsonNode`. `ApplicationConfiguration.json()` returns a copy. |
| `toObjectNode()`, `toArrayNode()` | The node as that type, as an `Optional`. `object()` and `array()` return the node itself, and throw `ClassCastException` for a node of another type and `NullPointerException` for an empty element. |

Read values. For the number and boolean reads a value may be a JSON number or boolean, or text that holds one.
The string reads take text only: a JSON number is not a string.

| Method | Meaning |
|---|---|
| `requireString(key)`, `requireInt(key)`, `requireBoolean(key)` | A value that must be there. See [Read a value](#read-a-value). |
| `asString()`, `asInt()`, `asLong()`, `asFloat()`, `asDouble()`, `asBoolean()` | The value of this element as an `Optional`. |
| `asString(default)`, `asInt(default)`, .. | The value of this element, or the default when it is missing or not of that type. |
| `asString(key, default)`, `asInt(key, default)`, .. | The same for the child at `key`. |
| `asClass()` | The class named by the text value, or `null` when the element is not text. Throws `RuntimeException` when the class is not found. |
| `toList(mapper)`, `toSet(mapper)` | The elements of an array, mapped. Empty when the element is not an array. |
| `toMap(mapper)` | The fields of an object, mapped. Empty when the element is not an object. |

```java
JsonElement document = JsonElement.ofStrict(new ObjectMapper().readTree("""
        {"n": {"whole": 7, "wholeText": "42"}, "list": ["a", "b"]}
        """));

int whole = document.requireInt("n.whole");            // 7
int fromText = document.asInt("n.wholeText", -1);      // 42
int missing = document.asInt("n.missing", -1);         // -1
String second = document.with("list").at(1).asString(null);   // "b"
List<String> all = document.with("list").toList(JsonNode::asText);
```

`readTree` throws the checked `JsonProcessingException`.

For booleans only the text `true`, in any letter case, is true; any other text is false. That includes the empty
node of `ofEphemeral`: there `asBoolean(default)` returns `false` for a key that does not exist, whatever the
default.

Use the `as..(default)` methods for your own JSON documents. For configuration keys use the `require..` methods,
so that a default exists in one place only: the defaults file.

Two static helpers work on an `ObjectNode`: `JsonElement.asMap(objectNode)` returns its direct fields, and
`JsonElement.toFlattenedMap(result, prefix, objectNode, mapper)` writes nested objects into `result` with dotted
keys.

## Reference: keys declared by this module

This module's `META-INF/stride/defaults.properties` declares the keys that the config, api and core modules read.
The optional modules declare their own; see [security.md](security.md) and [openapi.md](openapi.md). What the
server does with these settings is described in [core.md](core.md).

### Application

| Key | Default | Meaning |
|---|---|---|
| `logging.verbose` | `false` | With debug logging on, log which services are enabled and configured. |
| `application.alias` | `default` | The application's name. The OpenAPI module uses it in the document title. |
| `application.version` | `unknown` | The application's version. The OpenAPI module uses it as the document version. |
| `application.url` | empty | The address clients reach the application at, for example `https://api.example.com/v1`. Empty means not known. The OpenAPI module lists it as the server. |

### Services

| Key | Default | Meaning |
|---|---|---|
| `services.hk2.defaults.enabled` | `true` | Read when Stride decides whether a named service is enabled. Do not rely on it: give each named service its own `services.<name>.enabled`. |
| `services.hk2.threadpolicy` | `FULLY_THREADED` | The threading policy of the HK2 run level controller (`RunLevelController.ThreadingPolicy`). |
| `services.hk2.threadcount` | `20` | How many services of one run level may start at the same time, each on its own virtual thread. |
| `services.hk2.SystemInjectResolver.enabled` | `true` | Keeps the HK2 service of that name enabled. |
| `services.hk2.HK2TopicDistributionService.enabled` | `true` | Keeps the HK2 service of that name enabled. |
| `services.hk2.DefaultRunLevelContext.enabled` | `true` | Keeps the HK2 service of that name enabled. |

### Jetty

| Key | Default | Meaning |
|---|---|---|
| `services.jetty.server.enabled` | `true` | Whether the service that assembles the Jetty server (`jetty.server`) is enabled. |
| `services.jetty.start.enabled` | `true` | Whether the service that starts the listener (`jetty.start`) is enabled. |
| `services.jetty.server.config.host` | `localhost` | The address to listen on. |
| `services.jetty.server.config.port` | `0` | The port. `0` lets the operating system pick a free one. |
| `services.jetty.server.config.context-path` | `/` | The path every route is under. |
| `services.jetty.server.config.minThreads` | `10` | The smallest number of platform threads that accept connections and select I/O. Requests run on virtual threads, so this does not limit concurrent requests. |
| `services.jetty.server.config.maxThreads` | `150` | The largest number of those platform threads. Does not limit concurrent requests either. |
| `services.jetty.server.config.outputBufferSize` | `32768` | The response output buffer, in bytes. |
| `services.jetty.server.config.requestHeaderSize` | `16384` | The largest request header, in bytes. |
| `services.jetty.server.config.idleTimeout` | `PT30S` | How long a connection may be idle, as an ISO-8601 duration. |
| `services.jetty.server.config.acceptQueueSize` | `1024` | How many new connections may wait to be accepted. One that arrives when the queue is full is refused or reset. The operating system caps the number. With `0` or less the listener gets the Java default of 50. |
| `services.jetty.server.config.stopTimeout` | `PT30S` | How long requests that are running when the server stops get to finish, as an ISO-8601 duration. |
| `services.jetty.server.config.http2.enabled` | `true` | HTTP/2 without TLS (h2c) next to HTTP/1.1. |
| `services.jetty.server.config.forwarded.enabled` | `false` | Trust the `Forwarded` and `X-Forwarded-*` headers. Enable only behind a proxy that sanitizes them. |

Nothing limits the number of concurrent requests by default. An application that needs a ceiling adds a filter
with a low order that admits a bounded number of requests.

### Jersey

| Key | Default | Meaning |
|---|---|---|
| `services.jersey.objectMapper.enabled` | `true` | Whether the service that makes a Jackson `ObjectMapper` injectable (`jersey.objectMapper`) is enabled. |
| `services.jersey.server.enabled` | `true` | Whether the service that registers servlets and REST resources (`jersey.server`) is enabled. |
| `services.jersey.server.config.mediaTypes.yaml` | `application/yaml` | Maps the URI extension `.yaml` to this media type. |
| `services.jersey.server.config.mediaTypes.txt` | `text/plain` | Maps `.txt` to this media type. |
| `services.jersey.server.config.mediaTypes.schema` | `application/schema+json` | Maps `.schema` to this media type. |
| `services.jersey.server.config.mediaTypes.html` | `text/html` | Maps `.html` to this media type. |
| `services.jersey.server.config.mediaTypes.json` | `application/json` | Maps `.json` to this media type. |

### CORS

| Key | Default | Meaning |
|---|---|---|
| `filters.cors.enabled` | `true` | Whether the CORS filter is registered. |
| `filters.cors.config.class` | `io.descoped.stride.application.cors.ApplicationCORSServletFilter` | The filter class. |
| `filters.cors.config.pathSpec` | `/*` | The paths the filter applies to. |
| `filters.cors.config.order` | `100` | The filter's position in the chain. |
| `filters.cors.config.dispatches.0` | `FORWARD` | First dispatcher type the filter applies to. |
| `filters.cors.config.dispatches.1` | `REQUEST` | Second dispatcher type the filter applies to. |
| `filters.cors.config.origin` | `*` | Allowed origins: `*`, or a comma-separated list. |
| `filters.cors.config.credentials` | `false` | Whether credentials are allowed. `true` requires explicit origins, not `*`. |
| `filters.cors.config.headers` | `origin, content-type, accept, authorization` | Allowed request headers. |
| `filters.cors.config.methods` | `GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD` | Allowed methods. |
| `filters.cors.config.max-age` | `86400` | How long, in seconds, a browser may cache a preflight answer. |

## Reference: keys of a declared component

These keys exist once a component of that name is declared, in code or in a file.

| Key | Written by |
|---|---|
| `services.<name>.enabled` | `Service.Builder.enabled` |
| `services.<name>.config.class` | `Service.Builder.clazz`, `className` |
| `services.<name>.config.runLevel` | `Service.Builder.runLevel` |
| `services.<name>.metadata.<property>` | `Service.Builder.metadata` |
| `filters.<name>.enabled` | `Filter.Builder.enabled` |
| `filters.<name>.config.class` | `Filter.Builder.clazz`, `className` |
| `filters.<name>.config.pathSpec` | `Filter.Builder.pathSpec` |
| `filters.<name>.config.order` | `Filter.Builder.order` |
| `filters.<name>.config.dispatches.<index>` | `Filter.Builder.dispatches` |
| `servlets.<name>.enabled` | `Servlet.Builder.enabled` |
| `servlets.<name>.config.class` | `Servlet.Builder.clazz`, `className` |
| `servlets.<name>.config.pathSpec` | `Servlet.Builder.pathSpec` |
| `servlets.<name>.config.binding.<attribute>` | `ServletContextBinding.Builder.bind` |
| `servlets.<name>.config.validation.requires.servletContextAttributes.<index>` | `ServletContextValidation.Builder.requireAttribute`, `requireAttributes` |
| `resources.<name>.enabled` | `Resource.Builder.enabled` |
| `resources.<name>.config.class` | `Resource.Builder.clazz`, `className` |
| `resources.<name>.config.args.<index>.class`, `.named` | `Args.Builder.arg` |
| `initializers.config.classes.<index>` | `ServletContextInitialization.Builder.initializerClass` |
| `initializers.config.validation.requires.servletContextAttributes.<index>` | `ServletContextValidation.Builder.requireAttribute`, `requireAttributes` |
| `initializers.config.validation.requires.namedServices.<index>.type`, `.named` | `ServletContextValidation.Builder.requireNamedService` |
