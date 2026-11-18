package io.descoped.stride.application.openapi;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** A JAX-RS class that is on the class path and that no test registers: the application does not serve it. */
@Path("/unregistered")
public class UnregisteredResource {

    @GET
    public String get() {
        return "never served";
    }
}
