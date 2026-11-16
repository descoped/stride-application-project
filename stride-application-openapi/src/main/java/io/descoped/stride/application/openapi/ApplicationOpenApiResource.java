package io.descoped.stride.application.openapi;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.swagger.v3.jaxrs2.integration.resources.BaseOpenApiResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.Components;
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
 * Resources.builder().resource(Resource.builder("openapi").enabled(true).clazz(ApplicationOpenApiResource.class))
 * }</pre>
 * The title and version come from {@code application.alias} and {@code application.version}; a server entry is
 * added only when {@code application.url} is set; and the bearer scheme is declared when {@code security.enabled}
 * is true.
 */
@Path("/openapi")
@Singleton // the description is built once, not for every request
public class ApplicationOpenApiResource extends BaseOpenApiResource {

    static final String BEARER_SCHEME = "bearerAuth";

    @Context
    ServletConfig config;

    @Context
    Application app;

    @Inject
    public ApplicationOpenApiResource(ApplicationConfiguration configuration) {
        openApiConfiguration(new SwaggerConfiguration()
                .openAPI(describe(configuration))
                .filterClass(ApplicationOpenApiSpecFilter.class.getName())
                .prettyPrint(true));
    }

    static OpenAPI describe(ApplicationConfiguration configuration) {
        ApplicationConfiguration.Application application = configuration.application();
        OpenAPI openAPI = new OpenAPI()
                .info(new Info().title(application.alias() + " API").version(application.version()));

        application.url().ifPresent(url -> openAPI.addServersItem(new Server().url(url)));

        // security.enabled is declared by stride-application-security; without that module there is no key
        if (configuration.with("security.enabled").asBoolean(false)) {
            openAPI.components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
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
