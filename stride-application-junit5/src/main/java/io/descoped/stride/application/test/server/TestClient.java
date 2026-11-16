package io.descoped.stride.application.test.server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Sends real HTTP requests to an application under test. Connecting, and every request up to its response headers,
 * has a timeout, so a request the application never answers fails the test instead of hanging the build. A response
 * whose body stalls after the headers is not covered.
 */
public final class TestClient implements AutoCloseable {

    /** How long connecting, and a request up to its response headers, may take unless another timeout is given. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final TestUriResolver server;
    private final Duration timeout;
    private final HttpClient client;

    private TestClient(TestUriResolver server, Duration timeout) {
        this.server = Objects.requireNonNull(server, "server");
        this.timeout = requirePositive(timeout);
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    static Duration requirePositive(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("The timeout must be positive, but is " + timeout);
        }
        return timeout;
    }

    public static TestClient create(TestUriResolver server) {
        return new TestClient(server, DEFAULT_TIMEOUT);
    }

    /**
     * @param timeout how long connecting, and a request up to its response headers, may take
     */
    public static TestClient create(TestUriResolver server, Duration timeout) {
        return new TestClient(server, timeout);
    }

    public TestUriResolver getTestUriResolver() {
        return server;
    }

    public Duration timeout() {
        return timeout;
    }

    /**
     * Sends a request with any method.
     *
     * @param headersKeyAndValue header names and values in turn; a name may be repeated
     * @throws TestClientException when the request could not be sent or timed out; the cause tells which
     */
    public <R> ResponseHelper<R> send(String method, String uri, HttpRequest.BodyPublisher bodyPublisher,
                                      HttpResponse.BodyHandler<R> bodyHandler, String... headersKeyAndValue) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.testURL(uri)))
                .timeout(timeout)
                .method(method, bodyPublisher);
        if (headersKeyAndValue.length > 0) {
            request.headers(headersKeyAndValue);
        }
        try {
            return new ResponseHelper<>(client.send(request.build(), bodyHandler));
        } catch (IOException e) {
            throw new TestClientException(method + " " + uri + " failed: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TestClientException(method + " " + uri + " was interrupted", e);
        }
    }

    private static HttpResponse.BodyHandler<String> text() {
        return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
    }

    private static HttpRequest.BodyPublisher text(String body) {
        return HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
    }

    public ResponseHelper<String> options(String uri, String... headersKeyAndValue) {
        return options(uri, text(), headersKeyAndValue);
    }

    public <R> ResponseHelper<R> options(String uri, HttpResponse.BodyHandler<R> bodyHandler,
                                         String... headersKeyAndValue) {
        return send("OPTIONS", uri, HttpRequest.BodyPublishers.noBody(), bodyHandler, headersKeyAndValue);
    }

    public ResponseHelper<String> head(String uri) {
        return head(uri, text());
    }

    public <R> ResponseHelper<R> head(String uri, HttpResponse.BodyHandler<R> bodyHandler) {
        return send("HEAD", uri, HttpRequest.BodyPublishers.noBody(), bodyHandler);
    }

    public ResponseHelper<String> put(String uri) {
        return put(uri, HttpRequest.BodyPublishers.noBody(), text());
    }

    public ResponseHelper<String> put(String uri, String body) {
        return put(uri, text(body), text());
    }

    public <R> ResponseHelper<R> put(String uri, HttpRequest.BodyPublisher bodyPublisher,
                                     HttpResponse.BodyHandler<R> bodyHandler) {
        return send("PUT", uri, bodyPublisher, bodyHandler);
    }

    public ResponseHelper<String> post(String uri) {
        return post(uri, HttpRequest.BodyPublishers.noBody(), text());
    }

    public ResponseHelper<String> post(String uri, String body) {
        return post(uri, text(body), text());
    }

    public <R> ResponseHelper<R> post(String uri, HttpRequest.BodyPublisher bodyPublisher,
                                      HttpResponse.BodyHandler<R> bodyHandler) {
        return send("POST", uri, bodyPublisher, bodyHandler);
    }

    public <R> ResponseHelper<R> postJson(String uri, HttpRequest.BodyPublisher bodyPublisher,
                                          HttpResponse.BodyHandler<R> bodyHandler) {
        return send("POST", uri, bodyPublisher, bodyHandler, "Content-Type", "application/json");
    }

    public <R> ResponseHelper<R> postForm(String uri, HttpRequest.BodyPublisher bodyPublisher,
                                          HttpResponse.BodyHandler<R> bodyHandler) {
        return send("POST", uri, bodyPublisher, bodyHandler, "Content-Type", "application/x-www-form-urlencoded");
    }

    public ResponseHelper<String> get(String uri, String... headers) {
        return get(uri, text(), headers);
    }

    public <R> ResponseHelper<R> get(String uri, HttpResponse.BodyHandler<R> bodyHandler,
                                     String... headers) {
        return send("GET", uri, HttpRequest.BodyPublishers.noBody(), bodyHandler, headers);
    }

    public ResponseHelper<String> delete(String uri) {
        return delete(uri, text());
    }

    public <R> ResponseHelper<R> delete(String uri, HttpResponse.BodyHandler<R> bodyHandler) {
        return send("DELETE", uri, HttpRequest.BodyPublishers.noBody(), bodyHandler);
    }

    /** Closes the connections. Requests still in progress are cut off. */
    @Override
    public void close() {
        client.shutdownNow();
    }
}
