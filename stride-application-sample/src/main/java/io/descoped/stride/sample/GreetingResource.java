package io.descoped.stride.sample;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * A REST resource that gets what it needs through its constructor.
 */
@Path("/greetings")
public final class GreetingResource {

    private final Greetings greetings;

    public GreetingResource(Greetings greetings) {
        this.greetings = greetings;
    }

    @GET
    @Path("{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Greetings.Greeting greet(@PathParam("name") String name) {
        return greetings.greet(name);
    }
}
