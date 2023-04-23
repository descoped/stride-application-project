package io.descoped.stride.application.openapi;

import io.descoped.stride.application.config.api.ApplicationConfiguration;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class ApplicationOpenApiResourceHelper {

    static String normalizeContextPath(String contextPath) {
        Objects.requireNonNull(contextPath);
        String c = contextPath;
        // trim leading slashes
        while (c.startsWith("/")) {
            c = c.substring(1);
        }
        // trim trailing slashes
        while (c.endsWith("/")) {
            c = c.substring(0, c.length() - 1);
        }
        // add single leading slash
        if (c.length() > 0) {
            c = "/" + c;
        }
        return c;
    }

    public static ApplicationOpenApiResource createOpenApiResource(ApplicationConfiguration configuration, int port) {
        Info info = new Info()
                .title(configuration.application().alias() + " API")
                .version(configuration.application().version());
        String contextPath = normalizeContextPath(configuration.server().contextPath());
        OpenAPI openAPI = new OpenAPI()
                .info(info);
        String applicationUrl = configuration.application().url();
        if (applicationUrl != null) {
            openAPI.addServersItem(new io.swagger.v3.oas.models.servers.Server().url(applicationUrl));
        }
        openAPI.addServersItem(new io.swagger.v3.oas.models.servers.Server() {
            @Override
            public String getUrl() {
                return "http://localhost:" + port + contextPath;
            }
        });
        Map<String, SecurityScheme> securitySchemes = new LinkedHashMap<>();
        securitySchemes.put("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .name("Authorization")
                .in(SecurityScheme.In.HEADER)
                .scheme("bearer"));
        openAPI.components(new Components().securitySchemes(securitySchemes));
        openAPI.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
        SwaggerConfiguration oasConfig = new SwaggerConfiguration()
                .openAPI(openAPI)
                .filterClass(ApplicationOpenApiSpecFilter.class.getName())
                .prettyPrint(true);
        ApplicationOpenApiResource openApiResource = (ApplicationOpenApiResource) new ApplicationOpenApiResource()
                .openApiConfiguration(oasConfig);
        return openApiResource;
    }
}
