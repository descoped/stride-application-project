/**
 * Test support: starts a real Stride application and calls it over HTTP.
 */
module stride.application.junit5 {

    requires transitive stride.application.api;
    // TestClient and ResponseHelper expose java.net.http types
    requires transitive java.net.http;

    requires org.glassfish.hk2.api;
    requires org.junit.jupiter.api;
    requires org.slf4j;

    exports io.descoped.stride.application.test.server;
    exports io.descoped.stride.application.test.tuple;
}
