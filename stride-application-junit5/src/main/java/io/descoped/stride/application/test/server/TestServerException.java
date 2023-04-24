package io.descoped.stride.application.test.server;

public class TestServerException extends RuntimeException {

    public TestServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public TestServerException(Throwable cause) {
        super(cause);
    }

    public TestServerException(String message) {
        super(message);
    }
}
