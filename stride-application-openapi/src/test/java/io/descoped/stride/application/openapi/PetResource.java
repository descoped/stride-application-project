package io.descoped.stride.application.openapi;

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
