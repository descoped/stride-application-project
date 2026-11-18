package io.descoped.stride.application.openapi;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Filter;
import io.swagger.v3.jaxrs2.integration.JaxrsApplicationScanner;
import io.swagger.v3.jaxrs2.integration.resources.BaseOpenApiResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletConfig;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Serves the OpenAPI document of the application's JAX-RS resources at {@code /openapi.json} and
 * {@code /openapi.yaml}, or at {@code /openapi} by {@code Accept} header. Register it like any other resource:
 * <pre>{@code
 * Resources.builder().resource(Resource.builder("openapi").clazz(ApplicationOpenApiResource.class))
 * }</pre>
 * The title and version come from {@code application.alias} and {@code application.version}; a server entry is
 * added only when {@code application.url} is set; and the bearer scheme is declared when a filter named
 * {@code security} authenticates requests. The document describes the resources registered with Stride, as
 * classes or as instances, and nothing else that happens to be on the class path.
 */
@Path("/openapi")
@Singleton // the description is built once, not for every request
public class ApplicationOpenApiResource extends BaseOpenApiResource {

    static final String BEARER_SCHEME = "bearerAuth";
    static final String SECURITY_FILTER = "security";

    @Context
    ServletConfig config;

    @Context
    Application app;

    @Inject
    public ApplicationOpenApiResource(ApplicationConfiguration configuration, Components components) {
        openApiConfiguration(new SwaggerConfiguration()
                .openAPI(describe(configuration, authenticates(configuration, components)))
                // Describe what the application serves: the classes and instances registered with Jersey. Swagger's
                // default also searches the whole class path for @Path and would describe classes nobody registered.
                .scannerClass(JaxrsApplicationScanner.class.getName())
                .filterClass(ApplicationOpenApiSpecFilter.class.getName())
                .prettyPrint(true));
    }

    /**
     * Whether requests are authenticated: a filter named {@code security}, the name the security module uses, is
     * enabled in the configuration or was handed in as an instance. This describes what is enforced, not what a
     * setting asks for.
     */
    static boolean authenticates(ApplicationConfiguration configuration, Components components) {
        return components.filters().containsKey(SECURITY_FILTER)
                || configuration.filters().filter(SECURITY_FILTER).map(Filter::isEnabled).orElse(false);
    }

    static OpenAPI describe(ApplicationConfiguration configuration, boolean authenticates) {
        ApplicationConfiguration.Application application = configuration.application();
        OpenAPI openAPI = new OpenAPI()
                .info(new Info().title(application.alias() + " API").version(application.version()));

        application.url().ifPresent(url -> openAPI.addServersItem(new Server().url(url)));

        if (authenticates) {
            openAPI.components(new io.swagger.v3.oas.models.Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
            openAPI.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
        }
        return openAPI;
    }

    // Stride maps the .json and .yaml suffixes to media types (services.jersey.server.config.mediaTypes), so
    // /openapi.json and /openapi.yaml arrive here as /openapi with the matching Accept header.

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(hidden = true)
    public Response json(@Context HttpHeaders headers, @Context UriInfo uriInfo) throws Exception {
        return super.getOpenApi(headers, config, app, uriInfo, "json");
    }

    @GET
    @Produces("application/yaml")
    @Operation(hidden = true)
    public Response yaml(@Context HttpHeaders headers, @Context UriInfo uriInfo) throws Exception {
        return super.getOpenApi(headers, config, app, uriInfo, "yaml");
    }
}
