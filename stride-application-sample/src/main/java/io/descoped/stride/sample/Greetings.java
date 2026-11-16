package io.descoped.stride.sample;

/**
 * The application's own logic: a plain class that knows nothing about Stride, HTTP or injection.
 */
public final class Greetings {

    private final String salutation;

    public Greetings(String salutation) {
        this.salutation = salutation;
    }

    public Greeting greet(String name) {
        return new Greeting(salutation + ", " + name + "!");
    }

    public record Greeting(String message) {
    }
}
