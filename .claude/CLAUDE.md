# Stride Application

A tiny, lightweight, future-proof Java application stack for microservices and small monoliths.
The repository is a skeleton being finished into a production-ready stack.

## Philosophy (decides every change)

1. **Stay small.** Few dependencies, small API, little code. Core contains only what every application needs.
2. **Users choose their own libraries.** Stride provides a server and a lifecycle. It does not pick persistence,
   messaging, clients or validation for anyone. Anything optional lives in an optional module.
3. **Strong REST support.** gRPC is planned for later; do not start it unasked.
4. **Real-world testing.** The test library wraps a real, started server. Services are proven by real requests.
   Mocks may supply synthetic test data; they do not stand in for the service.
5. **Future-proof.** Track the newest Java. Structured concurrency and use of all cores matter greatly: no
   `ThreadLocal` or pooled-thread assumptions, nothing that pins or blocks virtual threads needlessly.

Stride is not Spring or Micronaut, and not a Jakarta EE platform or compliance effort.

## Hard rules

- **Dependencies are the owner's decision.** Never add, swap or upgrade-to-a-different library on your own. Present
  the options with the transitive jars each brings, then wait. A library that drags in many jars is a defect.
- **No roadblocks to forward passing.** Core uses HK2, and that stays. But users must be able to wire their own
  services by passing arguments and hand Stride ready-made instances, using HK2 injection only when they want it.
  Never make a component obtainable only through the locator or only by class name.
- **Standards, completely.** OIDC/JWT must be fully standards compliant, and OAuth means 2.1 only. Read the
  specification text before implementing; cite the section in the test name or a comment.
- **Finish what you touch.** No half-wired features, no special cases by class-name string, no assertion-free tests.
- **Simplicity.** Do not add features, configuration keys, modules or abstractions that were not asked for.

## Build

The baseline is Java 25: `maven.compiler.release` is 25 and nothing else specifies a Java version. Use what Java 25
has finalized; preview features are not allowed in published code. If the default JDK differs, set `JAVA_HOME` for
every Maven and Java command:

    JAVA_HOME="$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home" mvn -B -ntp clean verify

One module: add `-pl stride-application-core -am`. One test: add `-Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false`.
Dependency footprint: `mvn -o dependency:tree -Dscope=runtime -pl stride-application-core`.

## Modules

| Module | Role |
|---|---|
| `stride-application-parent` | Shared parent POM: versions, plugins, publishing. Every version is a property here. |
| `stride-application-config` | Configuration on `dynamic-configuration`, builders for services, filters, servlets, resources, and the module defaults mechanism. |
| `stride-application-api` | `StrideApplication` lifecycle contract and the `ApplicationInitializer` SPI (ServiceLoader). |
| `stride-application-junit5` | Test support: `StrideExtension` (JUnit extension that starts a real application and injects it and a `TestClient` with timeouts), `TestApplication`, `ResponseHelper`. |
| `stride-application-core` | Runtime: HK2 run levels, Jetty 12.1 (EE11) with HTTP/2, Jersey 4, CORS. No security, logging backend, metrics, WebSocket, XML or OpenAPI: applications add what they use. |
| `stride-application-openapi` | Optional: serves the OpenAPI document (Swagger) at `/openapi.json` and `/openapi.yaml`. |
| `stride-application-security` | Optional: JWT bearer authentication for a resource server (Nimbus JOSE+JWT). |
| `stride-application-sample` | A small application built on Stride with its test; the README is written against it. Not an artifact: the builds that publish pass `-pl '!:stride-application-sample'`, and the sample's POM fails a build that has both the sample and the `central` profile. |

The root `pom.xml` only aggregates; modules inherit from `stride-application-parent`.
Run levels: services up to 9, web assembly at 10, listener start at 11 (`RunLevelConstants`).

## Configuration

Layers, lowest first: the `META-INF/stride/defaults.properties` of every module on the class path; components
declared in code with the builders;
the application's sources (files, then environment variables, then system properties); `overrideProperty`.
Every key a module reads must be declared, with its default, in that module's own
`META-INF/stride/defaults.properties`; a key declared by two modules is an error. There are no
fallback values in code: read keys with `requireString`, `requireInt` or `requireBoolean`. Environment variables and
system properties can only override declared keys, so an undeclared key cannot be configured from outside.

## Conventions

- Tests start a real application and assert on status and body.
- `jakarta.*` only. The old javax/jakarta coexistence workarounds are debt to remove, not patterns to copy.
- A change in public behaviour or defaults is called out explicitly and gets its own commit.
- One concern per commit. Commit or push only when asked.
- Plans, analyses and reports do not go in this repository.
