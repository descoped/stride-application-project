package io.descoped.stride.application.core;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/greet")
public class GreetingResource {

    public GreetingResource() {
    }

    @GET
    @Path("/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    @Timed
    public Greeting greet(@PathParam("name") String name,
                          @QueryParam("greeting") String greetingParam) {
        if (greetingParam != null) {
            return new Greeting(name, greetingParam);
        }
        throw new UnsupportedOperationException();
    }

    public static class Greeting {

        final String name;
        final String greeting;

        @JsonCreator
        public Greeting(@JsonProperty("name") String name, @JsonProperty("greeting") String greeting) {
            this.name = name;
            this.greeting = greeting;
        }

        public String getName() {
            return name;
        }

        public String getGreeting() {
            return greeting;
        }

        @Override
        public String toString() {
            return "Greeting{" +
                    "name='" + name + '\'' +
                    ", greeting='" + greeting + '\'' +
                    '}';
        }
    }
}
