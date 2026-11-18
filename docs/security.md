# Security: JWT bearer authentication

`stride-application-security` makes an application an OAuth 2.1 resource server: a server that accepts requests
carrying an access token issued by an authorization server. The module accepts JWT access tokens sent as bearer
tokens, validates them with Nimbus JOSE+JWT, and gives the resources the caller's identity, scopes and roles. Add it
when the application must reject requests that carry no valid token. It is optional, and off until it is enabled.

The module does not issue tokens, log users in or manage sessions. It only validates the tokens that another
server issued.

## Dependency

```xml
<dependency>
    <groupId>io.descoped.stride.application</groupId>
    <artifactId>stride-application-security</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

An application with a module descriptor adds `requires stride.application.security;`. The classes are in the
package `io.descoped.stride.application.security`.

## Switch authentication on

Add the module, then set three keys, for example in `application.properties` (see [config.md](config.md) for the
configuration sources):

```properties
security.enabled=true
security.issuer=https://identity.example.com/realms/shop
security.audiences=https://api.example.com
```

- `security.issuer` is the issuer identifier of the authorization server: the https URL it puts in the `iss` claim
  of its tokens.
- `security.audiences` lists the identifiers of this application. A token is accepted only when its `aud` claim
  contains one of them.

A fuller example:

```properties
security.enabled=true
security.issuer=https://identity.example.com/realms/shop
security.audiences=https://api.example.com
security.required-scopes=orders:read
security.public-paths=/ping
```

`security.required-scopes` is space separated. The other lists (`security.audiences`, `security.algorithms`,
`security.public-paths`) are comma separated.

With `security.enabled=true` Stride creates a servlet filter named `security` of the class
`BearerAuthenticationFilter`. The filter covers every path (`/*`), so it also covers the `/ping` health check and
every servlet, not only the REST resources. It runs at order 200: after the CORS filter (100) and before the
application's own filters (default order 1000). Because CORS runs first, a browser's preflight request is answered
without a token, and a rejected request still carries the CORS headers. See [core.md](core.md) for filters and
their order.

Without `security.enabled=true` the filter is not created, no request is authenticated, and
`SecurityContext.getUserPrincipal()` returns `null` in a resource.

## How the keys are found: issuer discovery

When the application starts, the filter Stride creates reads the issuer's metadata to find its signing keys. An
application that supplies the keys itself skips this: see [Hand in the filter yourself](#hand-in-the-filter-yourself).
The issuer is taken from the configuration only, never from a token. Two locations are tried, in this order
(RFC 8414 section 5):

1. `/.well-known/oauth-authorization-server` inserted between the host and the path of the issuer (RFC 8414
   section 3.1). A slash that ends the issuer is removed first.
2. `/.well-known/openid-configuration` appended to the issuer (OpenID Connect Discovery 1.0 section 4).

For `security.issuer=https://identity.example.com/realms/shop` these are:

```text
https://identity.example.com/.well-known/oauth-authorization-server/realms/shop
https://identity.example.com/realms/shop/.well-known/openid-configuration
```

A metadata document is used only when its `issuer` value is identical to `security.issuer`, character for character
(RFC 8414 section 3.3). A trailing slash or a difference in letter case makes it a different issuer, and the next
location is tried. The document's `jwks_uri` must be an absolute https URL without credentials, query or fragment.

The keys are then fetched from `jwks_uri` before the application accepts requests, so the first authenticated
request needs no call to the authorization server. Facts worth knowing when operating the application:

- Requests to the authorization server have a 2 second connect timeout and a 3 second read timeout, read at most
  1 MB, and do not follow redirects.
- The keys are cached for 5 minutes. A token signed with a key id that is not in the cache makes the module fetch
  the keys again, at most once every 30 seconds. This is how a key rotation at the authorization server is picked
  up.
- If no usable metadata is found, the keys cannot be fetched, or the issuer publishes no public keys, the
  application does not start. See [Startup errors](#startup-errors).

## What a valid token is

The token must be a signed JWT. All of the following must hold:

| Check | Rule |
|---|---|
| Length | At most 16384 characters. |
| Signature | Made with a key the issuer publishes, or one of the keys the application supplied, using an algorithm listed in `security.algorithms`. An unsigned token is rejected. |
| `typ` header | Decided by `security.token-type`, see below. |
| `iss` | Equal to `security.issuer`. |
| `aud` | Contains at least one of `security.audiences`. |
| `exp` | Present and not passed. |
| `iat` | Present and not in the future. |
| `nbf` | If present, not in the future. |
| `sub` | Present and not blank. |
| Scope and roles claims | If present, a string or an array of strings. |

`security.clock-skew-seconds` is the tolerance for the time checks.

### Token type

`security.token-type` has two values.

- `at+jwt` (default). The tokens follow the JWT profile for OAuth 2.0 access tokens, RFC 9068. The `typ` header must
  be `at+jwt` or `application/at+jwt`; any other value is rejected (RFC 9068 section 4). This is also what keeps an
  OpenID Connect ID token from being accepted as an access token. The token must carry all of `iss`, `exp`, `aud`,
  `sub`, `client_id`, `iat` and `jti` (RFC 9068 section 2.2).
- `JWT`. For an authorization server that does not follow the profile. The `typ` header must be `JWT`; a token
  without a `typ` header is rejected. The token must carry `iss`, `exp`, `aud`, `sub` and `iat`; `client_id` and
  `jti` are not required. With this setting nothing but the audience tells an access token from an ID token, so
  choose `security.audiences` with care.

### Algorithms

`security.algorithms` lists the signature algorithms that are accepted. The default is `RS256`. Allowed values are
`RS256`, `RS384`, `RS512`, `PS256`, `PS384`, `PS512`, `ES256`, `ES384` and `ES512`. Symmetric algorithms such as
`HS256`, and `none`, are refused at startup.

### Scopes and roles

Scopes are the permissions the token grants. They are read from the claim named by `security.scope-claim`, default
`scope` (RFC 9068 section 2.2.3). Roles are read from the claim named by `security.roles-claim`, default `roles`
(RFC 9068 section 2.2.3.1).

Either claim may be a string with values separated by spaces, as `scope` is defined (RFC 8693 section 4.2), or a
JSON array of strings. A missing claim means no scopes or no roles. Any other shape makes the token invalid.

Only the configured claim is read. There is no fallback to another claim: for an issuer that puts the scopes in
`scp`, set `security.scope-claim=scp`.

`security.required-scopes` lists scopes that every authenticated request needs. The token must hold all of them.
A token that lacks one is answered with 403. The list is space separated (OAuth 2.1 section 1.4.1), because a
scope value may itself contain a comma.

## Leave paths open

`security.public-paths` lists paths that need no token, comma separated:

```properties
security.public-paths=/ping, /health
```

- A path is matched exactly, against the request path inside the application's context path. `/ping` does not open
  `/ping/child` or `/ping;x=1`.
- A path must start with `/` and cannot contain `*`, `;`, `%` or `..`. There are no patterns.
- A request to a public path is passed on as it is. Its token, if it sends one, is not read, so the resource sees
  no principal.

## What the caller gets when a request is rejected

The token is accepted from the `Authorization` header only: `Authorization: Bearer <token>`. The scheme name is
matched in any letter case, and one or more spaces may separate it from the token (OAuth 2.1 section 5.1.1). A
token in the query string (`?access_token=...`) is ignored, as OAuth 2.1 section 5.1 requires, and the form-encoded
body method of section 5.1.2 is not supported.

Every rejection has a `WWW-Authenticate` header with a `Bearer` challenge (OAuth 2.1 section 5.3.1),
`Cache-Control: no-store`, and an empty body. The responses follow OAuth 2.1 (draft-ietf-oauth-v2-1-16) section
5.3.2 and RFC 9068 section 4.

| Request | Status | `WWW-Authenticate` |
|---|---|---|
| No `Authorization` header | 401 | `Bearer realm="<realm>"` |
| Another authentication scheme, for example `Basic` | 401 | `Bearer realm="<realm>"` |
| More than one `Authorization` header | 400 | `Bearer realm="<realm>", error="invalid_request", error_description="More than one Authorization header"` |
| `Bearer` followed by nothing, or by something that is not a token | 400 | `Bearer realm="<realm>", error="invalid_request", error_description="The Authorization header is not a Bearer token"` |
| Expired token | 401 | `Bearer realm="<realm>", error="invalid_token", error_description="The access token expired"` |
| Token invalid for any other reason | 401 | `Bearer realm="<realm>", error="invalid_token", error_description="The access token is invalid"` |
| Valid token that lacks a required scope | 403 | `Bearer realm="<realm>", error="insufficient_scope", error_description="The access token lacks a required scope", scope="<required scopes>"` |

- The realm is `security.realm`. Its default is the application's alias, `application.alias`.
- The reason a token is invalid is not told to the caller. Only expiry is named.
- The `scope` attribute holds all of `security.required-scopes`, sorted and separated by spaces.
- When `security.resource` is set, every challenge ends with `resource_metadata="<url>"`, see the next section.

A request that is rejected does not reach the application's filters or resources.

## Publish protected resource metadata

Protected resource metadata (RFC 9728) is a JSON document that tells a client which authorization server issues
tokens for this application and which scopes to ask for. The module publishes it when `security.resource` is set.

`security.resource` is the resource identifier of the application: the https URL clients reach it at
(RFC 9728 section 1.2). It may have a path and a query, and must not have a fragment or credentials.

```properties
security.resource=https://api.example.com/orders
security.audiences=https://api.example.com/orders
```

A token's audience is normally this same URL, so it is usually also one of `security.audiences`.

The document is served at `/.well-known/oauth-protected-resource` followed by the path of the identifier
(RFC 9728 section 3.1). A lone `/` after the host is dropped.

| `security.resource` | Path of the document |
|---|---|
| `https://api.example.com` | `/.well-known/oauth-protected-resource` |
| `https://api.example.com/` | `/.well-known/oauth-protected-resource` |
| `https://api.example.com/orders` | `/.well-known/oauth-protected-resource/orders` |

With the issuer and the required scope of the [first example](#switch-authentication-on), the document is:

```json
{
  "resource": "https://api.example.com/orders",
  "authorization_servers": ["https://identity.example.com/realms/shop"],
  "scopes_supported": ["orders:read"],
  "bearer_methods_supported": ["header"]
}
```

- `resource` is `security.resource`, unchanged. A client uses the document only when this value is identical to
  the identifier it started from (RFC 9728 section 3.3).
- `authorization_servers` holds `security.issuer`.
- `scopes_supported` holds `security.required-scopes`, sorted. It is left out when no scope is required.
- `bearer_methods_supported` is always `["header"]`.

How it is served (RFC 9728 sections 3.1 and 3.2):

- No token is needed. `GET` answers 200 with `Content-Type: application/json`. `HEAD` answers the same without a
  body. Any other method answers 405 with `Allow: GET, HEAD`.
- Only the exact location is open. Other paths under `/.well-known/` need a token like any other path. If the
  identifier has a query, the request must carry the same query string.
- The path is inside the application's context path. The URL named in a challenge is built from the identifier
  only and does not contain the context path, so with a context path other than `/` a proxy must route the
  well-known URL to the application.

Every challenge then names the document (RFC 9728 section 5.1):

```text
WWW-Authenticate: Bearer realm="<realm>", resource_metadata="https://api.example.com/.well-known/oauth-protected-resource/orders"
```

With `security.resource` empty, no document is published and no challenge has a `resource_metadata` attribute.

## Use the caller's identity in a resource

For an authenticated request the filter sets the request's principal. A JAX-RS resource reads it from the
`SecurityContext`:

```java
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

@Path("/")
public class IdentityResource {

    @GET
    @Path("identity")
    public String identity(@Context SecurityContext security) {
        // "alice via Bearer"
        return security.getUserPrincipal().getName() + " via " + security.getAuthenticationScheme();
    }

    @GET
    @Path("admin")
    @RolesAllowed("admin")
    public String admin() {
        return "admin";
    }
}
```

- `getUserPrincipal()` returns a `TokenPrincipal`. Its name is the token's `sub` claim.
- `getAuthenticationScheme()` returns `Bearer`.
- `isUserInRole(role)` is true when the token's roles claim holds that role.
- `@RolesAllowed` works: [core](core.md) registers Jersey's support for it, and the roles come from the token's
  roles claim. A caller without one of the named roles gets 403.

`TokenPrincipal` is a record with the subject, the scopes and the roles. Cast the principal to read the scopes:

```java
import io.descoped.stride.application.security.TokenPrincipal;

TokenPrincipal caller = (TokenPrincipal) security.getUserPrincipal();
boolean mayWrite = caller.scopes().contains("orders:write");
```

The token itself and its other claims are not kept. `@RolesAllowed` checks roles, not scopes. Scopes are checked
by `security.required-scopes` for all requests, or by the resource's own code as above.

In a servlet or a servlet filter that runs after authentication, the same values come from
`HttpServletRequest`: `getUserPrincipal()`, `getRemoteUser()` (the subject), `getAuthType()` (`Bearer`) and
`isUserInRole(role)`.

If the application also uses [openapi.md](openapi.md), the OpenAPI document declares the bearer scheme when a
filter named `security` is in force.

## Hand in the filter yourself

An application that wires its own objects can create the filter and hand it to Stride under the name `security`
(see [api.md](api.md) for `Components`):

```java
import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.api.StrideApplication;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.security.BearerAuthenticationFilter;
import io.descoped.stride.application.security.JwtTokenVerifier;
import io.descoped.stride.application.security.SecurityConfiguration;

// the configuration must set security.enabled=true, security.issuer and security.audiences
ApplicationConfiguration configuration = ApplicationConfiguration.builder().defaults().build();
SecurityConfiguration settings = SecurityConfiguration.from(configuration);
JwtTokenVerifier verifier = JwtTokenVerifier.discover(settings);   // reads the issuer's metadata and keys

Components components = Components.builder()
        .filter("security", new BearerAuthenticationFilter(settings, verifier))
        .build();
StrideApplication.create(configuration, components).start();
```

- `SecurityConfiguration.from(configuration)` reads the `security.*` keys. The record can also be created with its
  constructor, without any configuration keys. Its components are, in order: `enabled`, `issuer`, `audiences`,
  `algorithms`, `tokenType`, `publicPaths`, `requiredScopes`, `scopeClaim`, `rolesClaim`, `realm`,
  `clockSkewSeconds`, `resource`. A second constructor leaves out `resource`, for an application that publishes no
  protected resource metadata.
- `JwtTokenVerifier.discover(settings)` throws `IOException` when the issuer's metadata or keys cannot be read.
- The instance uses the path, order and dispatcher types of `filters.security.*`, so it also runs at order 200.
- An instance that is handed in is always registered, whatever `filters.security.enabled` says, and takes the
  place of the filter Stride would create. Stride logs a warning when the configuration has the filter disabled.
  If the application wants a switch, it reads its own configuration and leaves the filter out.
- The settings must have `enabled` true. `SecurityConfiguration` validates its values only then, so settings
  that are not enabled cannot be used to authenticate: `JwtTokenVerifier.discover`, `JwtTokenVerifier.withKeys`
  and `new BearerAuthenticationFilter(settings, verifier)` throw an `IllegalArgumentException` whose message
  starts with `security.enabled is false`. Pass `true` when you build the record yourself.
- `new BearerAuthenticationFilter(settings, verifier)` throws `NullPointerException` for a `null` verifier.
- `JwtTokenVerifier` is `AutoCloseable`. The filter closes it in `destroy()`, which the server calls when the
  application stops.

### Supply the keys yourself

`JwtTokenVerifier.discover(settings)` fetches the keys from the issuer. `JwtTokenVerifier.withKeys(settings, keys)`
creates a verifier with keys the application supplies: a key set it holds, or its own way of fetching them.
Nothing is fetched from the issuer.

```java
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.File;

JWKSet keySet = JWKSet.load(new File("/etc/shop/issuer-keys.json"));   // a JWK Set document (RFC 7517)
JwtTokenVerifier verifier = JwtTokenVerifier.withKeys(settings, new ImmutableJWKSet<>(keySet.toPublicJWKSet()));

Components components = Components.builder()
        .filter("security", new BearerAuthenticationFilter(settings, verifier))
        .build();
```

- `keys` is a Nimbus `JWKSource<SecurityContext>` (`com.nimbusds.jose.jwk.source.JWKSource`,
  `com.nimbusds.jose.proc.SecurityContext`). The module brings Nimbus JOSE+JWT with it, and its module descriptor
  passes `com.nimbusds.jose.jwt` on.
- Everything else is checked as for a discovered verifier. A token must still name `security.issuer` in `iss`,
  and be signed with an algorithm listed in `security.algorithms`.
- Give the filter the same settings the verifier was built with. The verifier takes the issuer, audiences and
  algorithms from them; the filter takes the realm, scopes and public paths.
- The verifier owns the key source: `close()` closes it when it is a `java.io.Closeable`.
- `withKeys` throws `NullPointerException` for `null` keys.

`JwtTokenVerifier.verify(token)` can also be used on its own. It returns the `TokenPrincipal` of a valid token. For
an invalid one it throws a Nimbus exception: `ExpiredJWTException` for an expired token, otherwise another
`BadJOSEException`, a `JOSEException` or a `java.text.ParseException`.

## Startup errors

The settings are validated when the filter is created, before the server listens. A wrong value stops the
application: `start()` throws and no port is opened. Validation happens only when `security.enabled` is true.

| Message | Cause |
|---|---|
| `security.issuer is required when security.enabled is true` | `security.issuer` is empty. |
| `security.issuer is not a URL: '<value>'` | The issuer cannot be parsed as a URL. |
| `security.issuer must be an absolute https URL without credentials, query or fragment: '<value>'` | The issuer is not https, is relative, or has credentials, a query or a fragment. |
| `security.audiences is required` | `security.audiences` is empty. |
| `security.algorithms must contain supported asymmetric algorithms` | The list is empty or holds a value outside the allowed ones, for example `HS256` or `none`. |
| `security.token-type must be at+jwt or JWT` | Any other token type. |
| `security.clock-skew-seconds must be between 0 and 300` | The value is negative or above 300. |
| `security.public-paths must contain exact application paths` | A path does not start with `/`, or contains `*`, `;`, `%` or `..`. |
| `security.required-scopes holds a value that is not a scope: '<value>'` | A scope holds a character a scope cannot have, such as a quote or a backslash (OAuth 2.1 section 5.3.1). |
| `security.scope-claim is required`, `security.roles-claim is required` | The claim name is blank. |
| `security.realm is required and cannot contain a quote, a backslash or characters outside printable ASCII` | The realm is empty or cannot be quoted in a challenge. |
| `security.resource is not a URL: '<value>'` | The resource identifier cannot be parsed as a URL. |
| `security.resource must be an absolute https URL without credentials or fragment: '<value>'` | The identifier is not https, is relative, has credentials or a fragment, or holds a character that cannot be quoted in a challenge, such as a space. |
| `No usable authorization server metadata for issuer <issuer>` | Neither metadata location gave a document whose `issuer` is identical to `security.issuer` and whose `jwks_uri` is a valid https URL. |
| `Could not read the signing keys of issuer <issuer> from <jwks_uri>` | The keys could not be fetched from `jwks_uri`, or the key set there has no public signing keys. The cause then reads `The key set at <jwks_uri> has no public signing keys`. |
| `security.enabled is false, so the security settings are not validated and cannot be used to authenticate: set security.enabled=true, or leave the security filter out (filters.security.enabled=false)` | The filter was created from the configuration with `filters.security.enabled=true` while `security.enabled` is false, or the application built a verifier or a filter from settings that are not enabled. |

## Test a secured application

Tests start a real application and send real requests, see [testing.md](testing.md). Three facts decide how a
secured application is tested:

- Authentication is off unless `security.enabled=true`. A test that does not set it runs without authentication,
  and resources see no principal.
- The filter Stride creates from the configuration reads the issuer's metadata and keys over https when the
  application starts. A test that relies on it needs an authorization server it can reach over https, and tokens
  issued by it.
- A filter handed in under the name `security` takes the place of the configured one. A test can therefore hand
  in the real filter with a verifier built by `JwtTokenVerifier.withKeys`, and sign its own tokens. No
  authorization server is needed.

### Test with tokens the test signs

The test makes a key pair, gives the verifier the public half and signs tokens with the private half. The
application runs the real `BearerAuthenticationFilter`, so the test covers the token validation, the challenges,
the resources and `@RolesAllowed`. The settings come from the configuration, as in production; the issuer is
never contacted.

```java
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.security.BearerAuthenticationFilter;
import io.descoped.stride.application.security.JwtTokenVerifier;
import io.descoped.stride.application.security.SecurityConfiguration;
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecuredIdentityTest {

    static final String ISSUER = "https://identity.example.test/realms/shop";
    static final String AUDIENCE = "https://api.example.test";
    static final RSAKey KEY = key();

    @RegisterExtension
    static final StrideExtension stride = StrideExtension.builder()
            .configuration(() -> ApplicationConfiguration.builder().testDefaults()
                    .overrideProperty("security.enabled", "true")
                    .overrideProperty("security.issuer", ISSUER)
                    .overrideProperty("security.audiences", AUDIENCE)
                    .overrideProperty("security.realm", "shop"))
            .components(configuration -> {
                SecurityConfiguration settings = SecurityConfiguration.from(configuration);
                JwtTokenVerifier verifier = JwtTokenVerifier.withKeys(settings,
                        new ImmutableJWKSet<>(new JWKSet(KEY.toPublicJWK())));   // the public half only
                return Components.builder()
                        .filter("security", new BearerAuthenticationFilter(settings, verifier))
                        .resource(new IdentityResource())
                        .build();
            })
            .build();

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).keyID("test").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A token as the authorization server would issue it (RFC 9068), signed with the test's key. */
    private static String token(String subject, String... roles) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER).audience(AUDIENCE).subject(subject)
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                .claim("client_id", "test-client").jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of(roles))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEY.getKeyID()).type(new JOSEObjectType("at+jwt")).build(), claims);
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }

    @Test
    void aValidTokenReachesTheResource(TestClient client) throws Exception {
        String token = token("alice", "reader");

        assertEquals("alice via Bearer",
                client.get("/identity", "Authorization", "Bearer " + token).expect200Ok().body());
    }

    @Test
    void aRequestWithoutATokenGetsAChallenge(TestClient client) {
        var response = client.get("/identity").response();

        assertEquals(401, response.statusCode());
        assertEquals("Bearer realm=\"shop\"", response.headers().firstValue("WWW-Authenticate").orElseThrow());
    }

    @Test
    void aRoleTheCallerLacksIsForbidden(TestClient client) throws Exception {
        String token = token("alice", "reader");

        assertEquals(403, client.get("/admin", "Authorization", "Bearer " + token).response().statusCode());
    }
}
```

`IdentityResource` is the resource shown [above](#use-the-callers-identity-in-a-resource). With
`security.enabled=true` in the configuration the handed-in filter takes the place of the configured one, so
Stride does not create a filter that would look for the issuer.

### Test the resources with a known caller

When the token does not matter to the test, hand in a small filter of your own under the name `security`. It
sets the principal the way `BearerAuthenticationFilter` does. This tests the resources and `@RolesAllowed`, not
the token validation.

```java
import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.security.TokenPrincipal;
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityResourceTest {

    /** Test data: every request comes from alice, who has the role "reader". */
    static final class Alice extends HttpFilter {
        private final TokenPrincipal alice = new TokenPrincipal("alice", Set.of("orders:read"), Set.of("reader"));

        @Override
        protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(new HttpServletRequestWrapper(request) {
                @Override public Principal getUserPrincipal() { return alice; }
                @Override public String getAuthType() { return "Bearer"; }
                @Override public boolean isUserInRole(String role) { return alice.roles().contains(role); }
            }, response);
        }
    }

    @RegisterExtension
    static final StrideExtension stride = StrideExtension.builder()
            .components(() -> Components.builder()
                    .filter("security", new Alice())
                    .resource(new IdentityResource())
                    .build())
            .build();

    @Test
    void theResourceSeesTheCaller(TestClient client) {
        assertEquals("alice via Bearer", client.get("/identity").expect200Ok().body());
    }

    @Test
    void aRoleTheCallerLacksIsForbidden(TestClient client) {
        assertEquals(403, client.get("/admin").response().statusCode());
    }
}
```

### Test against a real authorization server

To test the application together with its authorization server, issuer discovery included, start the application
with `security.enabled=true` and an issuer the test can reach over https. `TestClient.get` takes request headers
as name and value pairs:

```java
@RegisterExtension
static final StrideExtension stride = StrideExtension.builder()
        .configuration(() -> MyApplication.configuration()
                .overrideProperty("security.enabled", "true")
                .overrideProperty("security.issuer", "https://identity.example.com/realms/shop")
                .overrideProperty("security.audiences", "https://api.example.com")
                .overrideProperty("security.realm", "shop"))
        .components(MyApplication::components)
        .build();

@Test
void aRequestWithoutATokenGetsAChallenge(TestClient client) {
    var response = client.get("/orders").response();

    assertEquals(401, response.statusCode());
    assertEquals("Bearer realm=\"shop\"", response.headers().firstValue("WWW-Authenticate").orElseThrow());
}

@Test
void aValidTokenReachesTheResource(TestClient client) {
    String token = tokenFromTheAuthorizationServer();   // the test's own way to get a token
    client.get("/orders", "Authorization", "Bearer " + token).expect200Ok();
}
```

`MyApplication.configuration()` and `MyApplication.components` stand for the application's own methods, as in
[sample.md](sample.md).

## Reference: configuration keys

All keys are declared in the module's `META-INF/stride/defaults.properties`. Environment variables and system
properties can override each of them, see [config.md](config.md).

| Key | Default | Meaning |
|---|---|---|
| `security.enabled` | `false` | Switches authentication on. |
| `security.issuer` | empty | The authorization server's issuer identifier: an absolute https URL without credentials, query or fragment. A token's `iss` must equal it. Required when enabled. |
| `security.audiences` | empty | The identifiers of this resource server, comma separated. A token's `aud` must contain one of them. Required when enabled. |
| `security.algorithms` | `RS256` | Accepted signature algorithms, comma separated. Allowed: `RS256`, `RS384`, `RS512`, `PS256`, `PS384`, `PS512`, `ES256`, `ES384`, `ES512`. |
| `security.token-type` | `at+jwt` | `at+jwt`: tokens follow RFC 9068. `JWT`: tokens of an authorization server that does not follow the profile. |
| `security.public-paths` | empty | Exact paths that need no token, comma separated. |
| `security.required-scopes` | empty | Scopes every authenticated request needs, space separated. |
| `security.scope-claim` | `scope` | The claim that holds the granted scopes. |
| `security.roles-claim` | `roles` | The claim that holds the roles. |
| `security.realm` | `${application.alias}` | The realm named in the `WWW-Authenticate` challenge. `application.alias` is `default` unless the application sets it. |
| `security.clock-skew-seconds` | `30` | Tolerance for the time checks of a token, 0 to 300 seconds. |
| `security.resource` | empty | This resource server's resource identifier (RFC 9728): an absolute https URL without credentials or fragment. When set, protected resource metadata is published and every challenge names it. |

The module also declares the filter that does the work. These keys seldom need to change.

| Key | Default | Meaning |
|---|---|---|
| `filters.security.enabled` | `${security.enabled}` | Whether Stride creates the filter. It follows `security.enabled`. |
| `filters.security.config.class` | `io.descoped.stride.application.security.BearerAuthenticationFilter` | The filter class. |
| `filters.security.config.pathSpec` | `/*` | The paths the filter covers. |
| `filters.security.config.order` | `200` | Its place among the filters: after CORS (100), before application filters (1000). |
| `filters.security.config.dispatches.0` to `.4` | `REQUEST`, `FORWARD`, `INCLUDE`, `ASYNC`, `ERROR` | The dispatcher types the filter applies to. |

## Reference: classes

All are in `io.descoped.stride.application.security`.

| Class | Use |
|---|---|
| `BearerAuthenticationFilter` | The servlet filter. Created by Stride from the configuration, or by the application with `new BearerAuthenticationFilter(settings, verifier)`. |
| `SecurityConfiguration` | Record with the validated settings. `SecurityConfiguration.from(configuration)` reads the `security.*` keys. `SecurityConfiguration.ACCESS_TOKEN_TYPE` is `at+jwt`. |
| `JwtTokenVerifier` | Validates tokens. `JwtTokenVerifier.discover(settings)` creates one from the issuer's metadata; `JwtTokenVerifier.withKeys(settings, keys)` creates one with keys the application supplies; `verify(token)` returns a `TokenPrincipal`; `close()` releases the key source. |
| `TokenPrincipal` | Record and `java.security.Principal`: `subject()`, `scopes()`, `roles()`. `getName()` is the subject. |

## Reference: specifications

| Specification | Used for |
|---|---|
| OAuth 2.1 (draft-ietf-oauth-v2-1-16), section 5 | How the token is sent (5.1, 5.1.1, 5.1.2) and how a request is rejected (5.3.1, 5.3.2). Section 1.4.1 for the space separated scope list. |
| RFC 9068, JWT profile for OAuth 2.0 access tokens | The `at+jwt` token type (2.1, 4), the required claims (2.2), the scope and roles claims (2.2.3, 2.2.3.1), `invalid_token` for every failed validation (4). |
| RFC 8414, authorization server metadata | Issuer identifier (2), metadata location (3.1), the identical `issuer` check (3.3), order of the two locations (5). |
| OpenID Connect Discovery 1.0, section 4 | The second metadata location. |
| RFC 9728, protected resource metadata | Resource identifier (1.2), the document (2, 3.2), its location (3, 3.1), the identical `resource` value (3.3), `resource_metadata` in a challenge (5.1). |
| RFC 8693, section 4.2 | `scope` as a space-delimited string. |
