package io.descoped.stride.sample;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;

/**
 * A REST resource that gets what it needs through its constructor.
 */
@Path("/greetings")
public final class GreetingResource {

    private final Greetings greetings;

    public GreetingResource(Greetings greetings) {
        this.greetings = greetings;
    }

    /**
     * Greets the caller. With authentication on, the caller is the subject of the access token; without it
     * there is no caller to name.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Greetings.Greeting greetCaller(@Context SecurityContext security) {
        Principal caller = security.getUserPrincipal();
        return greetings.greet(caller == null ? "stranger" : caller.getName());
    }

    @GET
    @Path("{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Greetings.Greeting greet(@PathParam("name") String name) {
        return greetings.greet(name);
    }
}
