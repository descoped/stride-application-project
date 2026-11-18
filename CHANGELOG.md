# Changelog

What changes for an application built on Stride. Changes that only concern Stride's own build are left out.

## 0.1.0 (not released yet)

The last release was 0.0.23, in February 2023. After it Stride was published only as `0.0.24-SNAPSHOT`, which is
now left as it is: the changes below are breaking, so development continues as `0.1.0-SNAPSHOT`.
This section is written for an application that built on those snapshots: Java 17, Jetty 11, Jersey 3.1.

### Before you upgrade

Most applications are affected by these.

- **Java 25 or newer is required.** It was 17.
- **Core no longer brings a logging backend.** Add the one you use, for example Logback or `slf4j-simple`. Core
  needs only `slf4j-api`.
- **Core no longer brings WebSocket, Dropwizard Metrics, JAXB or Swagger.** Declare the ones you use. Swagger is
  in the new optional module `stride-application-openapi`.
- **Authentication is in its own module.** An application that sets `security.*` must add
  `stride-application-security`.
- **The test library is `stride-application-junit-jupiter`.** It was `stride-application-junit5`.
- **Jetty 12.1 (EE11), Jersey 4 and HK2 4** replace Jetty 11, Jersey 3.1 and HK2 3. Code that uses their APIs
  directly, or the Servlet API, must build against Servlet 6.1 and JAX-RS 4.0.

### Platform

| | Before | Now |
|---|---|---|
| Java | 17 | 25 |
| Jetty | 11.0 | 12.1 (EE11) |
| Jersey | 3.1 | 4.0 |
| HK2 | 3.0 | 4.0 |
| Servlet API | 5.0 | 6.1 |
| Jackson | 2.15 | 2.23 |
| JUnit | 5.9 | 6.1 |

- Every request is handled on a new virtual thread, named `stride-http-N`. Run level services start on virtual
  threads named `stride-runlevel-N`.
- `services.jetty.server.config.minThreads` and `maxThreads` no longer limit the number of concurrent requests.
  Nothing does by default. An application that needs a ceiling adds a filter with a low order.
- `services.hk2.threadcount` bounds how many services of one run level start at the same time.
- Requests that are running when the server stops get time to finish. New key
  `services.jetty.server.config.stopTimeout`, default `PT30S`.
- A burst of new connections is no longer reset. New key `services.jetty.server.config.acceptQueueSize`, default
  `1024`: how many connections may wait to be accepted. It was the Java default of 50.

### Modules

- `stride-application-openapi` is new and optional. It serves the OpenAPI document at `/openapi.json` and
  `/openapi.yaml`. It adds a server entry only when `application.url` is set, and declares the bearer scheme only
  when a filter named `security` is in force.
- `stride-application-security` is new and optional. It holds bearer authentication and Nimbus JOSE+JWT. Core no
  longer depends on Nimbus JOSE+JWT or `java.net.http`.
- **`stride-application-junit5` is renamed `stride-application-junit-jupiter`.** Change the artifactId in your
  POM. The name follows the programming model, JUnit Jupiter, not a JUnit version: the module runs on JUnit 6.
  The Java packages are unchanged. It has a module descriptor, `stride.application.junit.jupiter`.

### Configuration

- **Configuration is built on `io.descoped.config:dynamic-configuration`.** It replaces Cantara property-config.
  `ApplicationConfiguration.Builder.configuration(..)` takes a `Consumer<DynamicConfiguration.Builder>`.
- **The order of precedence changed.** Lowest first:
  1. the `META-INF/stride/defaults.properties` of every module on the class path;
  2. components declared in code with the builders;
  3. the application's sources: files, then environment variables, then system properties;
  4. `overrideProperty`.

  Before, values from the builders were merged in after the environment and could not be overridden from outside,
  and arrays were concatenated.
- **Every key must be declared.** A module declares the keys it reads, with their defaults, in its own
  `META-INF/stride/defaults.properties`. An application or a library can do the same. A key declared by two
  modules is an error that names both files. An environment variable or a system property can override only a
  declared key.
- **There are no fallback values in code.** Keys are read with `requireString`, `requireInt` and
  `requireBoolean`.
- Every Stride key can now be set by an environment variable or a system property. About 30 keys could not.
- The environment variable for a key is the key in upper case with every character that is not a letter or a
  digit replaced by `_`: `services.jetty.server.config.port` is `SERVICES_JETTY_SERVER_CONFIG_PORT`.
- Stride's own defaults moved from `application-defaults.properties` to `META-INF/stride/defaults.properties`. A
  build that merges jars into one must append the files of that name, not keep one of them.
- `ApplicationConfiguration.builder().build()` without `defaults()` or `testDefaults()` carries Stride's defaults.
  It used to give an empty configuration.
- The default port is `0`, a free port picked by the operating system. The `9090` fallback in code is gone.
- `application.url` is a declared key, empty by default. `Application.url()` returns an `Optional<String>`. It
  used to invent `http://<host>:<port><context-path>`.
- Removed: `JsonMerger`, the constructors that took `ApplicationProperties`, and the `Application.Cors` accessor.
- Removed from core: `DefaultExceptionServletFilter` and `ObjectMapperFactory`. Stride never registered either,
  so they did nothing unless an application named them itself. An application that did keeps a copy of the
  class: both are a few lines.

### Components

- **An application can hand Stride ready-made objects.** `Components` holds resource, filter and servlet
  instances, and service instances offered for injection by contract and optional name. Pass it to
  `StrideApplication.create(configuration, components)`. HK2 injection still works and is optional.
- **`ApplicationInitializer.initialize` takes the components as a second argument.** Implementations must add
  the parameter.
- **Components declared in code are enabled by default.** `Service.builder(name)`, `Filter.builder(name)`,
  `Servlet.builder(name)` and `Resource.builder(name)` used to produce a component that was left out unless
  `.enabled(true)` was called. `.enabled(false)`, or `<kind>.<name>.enabled=false` from a later source, still
  disables one.
- **A filter or servlet instance is always registered.** `filters.<name>.enabled=false` does not remove an
  instance; Stride logs a warning. An application that wants a switch leaves the instance out.
- **Requirements declared on the servlet context initialization are checked.**
  `ServletContextInitialization.builder().validate(..)` was accepted and then ignored, because of a misspelled
  key inside Stride. A required attribute or named service that is missing now stops the application from
  starting, as the same requirement on a servlet always did.
- **A configured service class keeps its qualifiers.** A class named in `services.<name>.config.class` is bound
  with the qualifier annotations it carries, not only `@Named`. A `@MessageReceiver` class named there now
  receives HK2 topic events; it used to receive none.
- **A configured service class must not declare its own scope.** A class named in
  `services.<name>.config.class` that carries a scope annotation or HK2's `@Service` fails at
  `StrideApplication.create`, with a message naming the key. A `@Named` class is bound under its name.

### Filters

- **Filters run in ascending `config.order`, then by name.** CORS is at 100, authentication at 200, and a filter
  without an order at 1000. Application filters used to run in the order of the configuration's keys.
- A `config.order` that is not a whole number fails at startup. It used to become 1000 silently.
- A filter without `config.pathSpec` applies to `/*`, and one without `config.dispatches` to `REQUEST`. Both used
  to fail at startup.
- An enabled filter with neither a class nor an instance fails at startup, naming the missing key.
- Filters are registered with the server, not with Jersey. They apply to every servlet and to `/ping`, also when
  `services.jersey.server.enabled` is false.

### Authentication

Bearer authentication follows OAuth 2.1 (draft-ietf-oauth-v2-1-16, section 5), RFC 9068 and RFC 8414.

- `SecurityConfiguration` moved to the package `io.descoped.stride.application.security`.
  `ApplicationConfiguration.security()` is replaced by `SecurityConfiguration.from(configuration)`.
- **`security.required-scopes` is space separated.** It was comma separated.
- **Scopes are read from the claim named by `security.scope-claim`**, default `scope`. The silent fallback to
  `scp` is gone: an issuer that puts scopes in `scp` needs `security.scope-claim=scp`.
- **With `security.token-type=at+jwt`, the claims `client_id` and `jti` are required**, besides `iss`, `exp`,
  `aud`, `sub` and `iat`. The `typ` header may be `at+jwt` or `application/at+jwt`.
- **Responses changed:**

  | Request | Before | Now |
  |---|---|---|
  | No credentials | 401, bare `Bearer` challenge | 401, `Bearer realm="…"`, no error code |
  | Another authentication scheme | 401 `invalid_token` | 401 with a challenge, no error code |
  | More than one `Authorization` header, or credentials that are not token68 | 401 `invalid_token` | 400 `invalid_request` |
  | Invalid or expired token | 401 `invalid_token` | 401 `invalid_token` with an `error_description`; an expired token says so |
  | Missing scope | 403 `insufficient_scope` | 403 `insufficient_scope` with an `error_description` and the required scopes |

- New key `security.realm`, default `${application.alias}`. The challenge always carries it.
- Issuer metadata is read from `/.well-known/oauth-authorization-server`, inserted between host and path, and
  then from the OpenID Connect location. Only the OpenID Connect location was tried before.
- Required scopes and the realm are validated at startup against the characters a challenge may carry.
- A missing or malformed `security.issuer` fails at startup with a message that names the key and the value.
  It used to fail with a `NullPointerException` or a message that did not say which setting was wrong.
- **Protected resource metadata (RFC 9728) can be published.** New key `security.resource`, empty by default:
  the https URL clients reach the application at. When it is set, the metadata is served without a token at
  `/.well-known/oauth-protected-resource` followed by the URL's path, and every challenge names the document in
  `resource_metadata`. With the key empty nothing changes.

### Test library

- **`StrideExtension` is new.** `@ExtendWith(StrideExtension.class)` starts a real application with the test
  defaults. `StrideExtension.builder()` in a `@RegisterExtension` field takes the configuration, `Components` and
  the client timeout. Tests get `TestClient`, `TestApplication`, `StrideApplication` and `TestUriResolver` as
  parameters.
- **Test requests time out.** Connecting, and each request up to its response headers, times out after 30
  seconds, or after the duration given to `TestClient.create(server, timeout)`. There was no timeout before.
- A failed request is thrown once as `TestClientException`, with a message naming the method and path.
- `TestClient` is `AutoCloseable` and has one public `send(method, uri, body, handler, headers...)` for methods
  the helpers do not cover.
- `TestServerException` is removed.
- **The package `io.descoped.stride.application.test.tuple` is removed.** Nothing in Stride used it, and a test
  library is not the place for it. For ordered tuples and order-preserving binary keys, add
  `io.descoped.tuple:java-tuple` to your own build. Its API and its encoding (FoundationDB compatible) are
  different, so code that used the old classes must be rewritten against it.
- `ResponseHelper` reports the expected and the actual status the right way round.
