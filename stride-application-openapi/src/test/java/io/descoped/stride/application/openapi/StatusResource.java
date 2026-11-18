package io.descoped.stride.application.openapi;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** An operation that needs no token although the application authenticates. */
@Path("/status")
public class StatusResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @SecurityRequirement(name = ApplicationOpenApiSpecFilter.NO_AUTHENTICATION)
    public String status() {
        return "up";
    }
}
