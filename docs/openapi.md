# OpenAPI

`stride-application-openapi` serves the OpenAPI document of your application: a machine-readable description of
its REST resources, their paths, parameters and responses. The document is produced by Swagger from the JAX-RS
annotations on your resource classes. Add the module when clients, gateways or documentation tools need that
description. It is optional: [core](core.md) does not bring Swagger.

## Maven dependency

```xml
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-openapi</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The module brings `swagger-jaxrs2-jakarta`, and with it the Swagger annotations.

An application that has a module descriptor adds:

```java
requires stride.application.openapi;
```

and, when its own classes use the Swagger annotations:

```java
requires io.swagger.v3.oas.annotations;
```

## Serve the document

Adding the jar is not enough. The module has one resource class, `ApplicationOpenApiResource`, and nothing
registers it for you. Register it by class, like any other resource (see [config.md](config.md)):

```java
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.openapi.ApplicationOpenApiResource;

ApplicationConfiguration configuration = ApplicationConfiguration.builder()
        .defaults()
        .resources(Resources.builder()
                .resource(Resource.builder("pets").clazz(PetResource.class))
                .resource(Resource.builder("openapi").clazz(ApplicationOpenApiResource.class)))
        .build();
```

A resource declared with the builder is enabled unless it says otherwise, so `.enabled(true)` is not needed.

Jersey creates the resource and injects the `ApplicationConfiguration` and the `Components` into its constructor.

The document is then served at three addresses:

| Request | Answer |
|---|---|
| `GET /openapi.json` | The document as JSON, `application/json` |
| `GET /openapi.yaml` | The document as YAML, `application/yaml` |
| `GET /openapi` | JSON or YAML, chosen by the `Accept` header |

The `.json` and `.yaml` suffixes work because Stride maps them to media types, with the keys
`services.jersey.server.config.mediaTypes.json` and `services.jersey.server.config.mediaTypes.yaml`
(see [Configuration keys](#configuration-keys)).

The document is pretty-printed. The resource is a singleton, so the description is built once, not for every
request. It does not list `/openapi` itself.

Swagger finds the resource classes itself. It takes those registered with Jersey, and it also scans the class path
for classes annotated with `@Path`. A resource class that is on the class path but not registered, or registered
disabled, can therefore appear in the document.

## Annotate your resources

Plain JAX-RS annotations are enough. This resource appears in the document as the path `/pets/{name}` with a
`get` operation:

```java
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/pets")
public class PetResource {

    public record Pet(String name) {
    }

    @GET
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Pet pet(@PathParam("name") String name) {
        return new Pet(name);
    }
}
```

To say more, add the Swagger annotations from the package `io.swagger.v3.oas.annotations`. They come with the
module. For example, `@Operation(hidden = true)` keeps a method out of the document; the module uses it on its
own two methods.

### Mark an operation as needing no token

When the document declares the bearer scheme (see [below](#declare-bearer-authentication)), every operation
requires it. To describe one operation as open, give it a single security requirement named `none`:

```java
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@GET
@Path("/status")
@SecurityRequirement(name = "none")
public String status() {
    return "ok";
}
```

The module replaces that requirement with an empty one, which in OpenAPI means "no security". This changes the
document only. Whether the path really needs a token is decided by the security filter: see
[security.md](security.md).

## Set the title and version

The title is the value of `application.alias` followed by ` API`. The version is `application.version`.

```properties
application.alias=petstore
application.version=1.2.3
```

gives

```yaml
info:
  title: petstore API
  version: 1.2.3
```

With the defaults the title is `default API` and the version is `unknown`.

## Add a server entry

A server entry tells clients the address the API is reached at. Stride cannot know it: the application may listen
on a port picked by the operating system and sit behind a proxy. So the document has no `servers` element unless
you set `application.url`:

```properties
application.url=https://api.example.test/v1
```

The document then has one server entry with that URL. An empty or blank value means "not known" and adds nothing.

## Declare bearer authentication

The document declares bearer authentication when a filter named `security` is in force. That is the name the
[security module](security.md) gives its authentication filter. It holds in either of two cases:

- the configuration has a filter named `security` that is enabled. With `stride-application-security` on the
  class path, that is the case when `security.enabled=true`, because the module declares
  `filters.security.enabled=${security.enabled}`;
- the application handed in a filter instance named `security` (`Components` is in
  `io.descoped.stride.application.api`):

  ```java
  Components components = Components.builder()
          .filter("security", myAuthenticationFilter)
          .build();
  ```

The document then has a security scheme named `bearerAuth` (type `http`, scheme `bearer`, bearer format `JWT`)
and a document-wide security requirement for it:

```json
{
  "components": {
    "securitySchemes": {
      "bearerAuth": { "type": "http", "scheme": "bearer", "bearerFormat": "JWT" }
    }
  },
  "security": [ { "bearerAuth": [] } ]
}
```

The document describes what is enforced, not what a setting asks for. Setting `security.enabled=true` without
the security module on the class path enables no filter, so nothing is declared.

## Turn it off

Do not register the resource, or register it disabled:

```java
Resource.builder("openapi").enabled(false).clazz(ApplicationOpenApiResource.class)
```

A disabled resource is not registered, so nothing answers at `/openapi`.

The builder's value is a default that the application's sources can override. A resource named `openapi` is
also switched off by a property, in `application.properties` or with `overrideProperty`:

```properties
resources.openapi.enabled=false
```

## Test it

Start the real application and read the document. `TestApplication` and `TestClient` are in
`stride-application-junit-jupiter`: see [testing.md](testing.md). The example parses the body with Jackson.

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.openapi.ApplicationOpenApiResource;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiTest {

    @Test
    void servesTheDocument() throws Exception {
        ApplicationConfiguration.Builder configuration = ApplicationConfiguration.builder()
                .testDefaults()
                .overrideProperty("application.alias", "petstore")
                .overrideProperty("application.version", "1.2.3")
                .resources(Resources.builder()
                        .resource(Resource.builder("pets").clazz(PetResource.class))
                        .resource(Resource.builder("openapi").clazz(ApplicationOpenApiResource.class)));

        try (TestApplication application = new TestApplication.Builder().configuration(configuration).build()) {
            application.start();

            String body = TestClient.create(application).get("/openapi.json").expect200Ok().body();
            JsonNode document = new ObjectMapper().readTree(body);

            assertEquals("petstore API", document.at("/info/title").asText());
            assertEquals("1.2.3", document.at("/info/version").asText());
            assertTrue(document.path("paths").has("/pets/{name}"), body);
        }
    }
}
```

## Reference

### Classes

Both are in the package `io.descoped.stride.application.openapi`.

| Class | Role |
|---|---|
| `ApplicationOpenApiResource` | The JAX-RS resource at `/openapi`. Register this one. |
| `ApplicationOpenApiSpecFilter` | Turns a single security requirement named `none` into "no security". Used by the resource; you do not register it. |

### Configuration keys

The module declares no keys of its own. It reads these, which the [config module](config.md) declares:

| Key | Default | Effect on the document |
|---|---|---|
| `application.alias` | `default` | The title is this value followed by ` API`. |
| `application.version` | `unknown` | The version. |
| `application.url` | empty | When set, the one server entry. When empty, no server entry. |
| `services.jersey.server.config.mediaTypes.json` | `application/json` | Makes `/openapi.json` answer with JSON. |
| `services.jersey.server.config.mediaTypes.yaml` | `application/yaml` | Makes `/openapi.yaml` answer with YAML. |

The bearer scheme depends on a key of the [security module](security.md):

| Key | Default | Effect on the document |
|---|---|---|
| `filters.security.enabled` | `${security.enabled}`, which is `false` | When `true`, the bearer scheme is declared. |

### What the document contains

| Element | Present when | Value |
|---|---|---|
| `info.title` | Always | `application.alias` followed by ` API` |
| `info.version` | Always | `application.version` |
| `paths` | Always | The JAX-RS resources registered with Jersey or found on the class path, without `/openapi` |
| `servers` | `application.url` is set | One entry with that URL |
| `components.securitySchemes.bearerAuth` | A filter named `security` is in force | `http`, `bearer`, `JWT` |
| `security` | A filter named `security` is in force | A requirement for `bearerAuth` |
