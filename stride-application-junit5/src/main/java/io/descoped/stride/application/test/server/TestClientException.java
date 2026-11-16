package io.descoped.stride.application.test.server;

public class TestClientException extends RuntimeException {

    public TestClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public TestClientException(Throwable cause) {
        super(cause);
    }

    public TestClientException(String message) {
        super(message);
    }
}
