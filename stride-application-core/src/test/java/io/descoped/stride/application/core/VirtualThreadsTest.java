package io.descoped.stride.application.core;

import io.descoped.stride.application.config.ApplicationConfiguration;
import io.descoped.stride.application.config.Resource;
import io.descoped.stride.application.config.Resources;
import io.descoped.stride.application.config.Service;
import io.descoped.stride.application.config.Services;
import io.descoped.stride.application.test.server.TestApplication;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualThreadsTest {

    @Path("/thread")
    public static class ThreadResource {

        private static final Object LOCK = new Object();
        static volatile CountDownLatch allArrived;

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String thread() {
            Thread thread = Thread.currentThread();
            return thread.isVirtual() + " " + thread.getName();
        }

        // Holds every request inside a synchronized block until all of them have arrived. With more requests than
        // the server has platform threads, this completes only if requests run on virtual threads.
        @GET
        @Path("/rendezvous")
        @Produces(MediaType.TEXT_PLAIN)
        public String rendezvous(@QueryParam("id") int id) throws InterruptedException {
            allArrived.countDown();
            boolean together = allArrived.await(10, TimeUnit.SECONDS);
            synchronized (LOCK) {
                Thread.sleep(1);
            }
            return together + " " + Thread.currentThread().isVirtual();
        }
    }

    public static class StartupThreadService {
        static volatile Thread startedOn;

        public StartupThreadService() {
            startedOn = Thread.currentThread();
        }
    }

    private static ApplicationConfiguration.Builder configuration() {
        return ApplicationConfiguration.builder()
                .testDefaults()
                .resources(Resources.builder()
                        .resource(Resource.builder("thread").enabled(true).clazz(ThreadResource.class)))
                .services(Services.builder()
                        .service(Service.builder("startupThread").enabled(true).clazz(StartupThreadService.class).runLevel(5)));
    }

    @Test
    void requestsAreHandledOnNamedVirtualThreads() {
        try (TestApplication application = new TestApplication.Builder().configuration(configuration()).build()) {
            application.start();

            String body = TestClient.create(application).get("/thread").expect200Ok().body();

            assertTrue(body.startsWith("true stride-http-"), body);
        }
    }

    @Test
    void servicesStartOnVirtualThreads() {
        try (TestApplication application = new TestApplication.Builder().configuration(configuration()).build()) {
            application.start();

            Thread startedOn = StartupThreadService.startedOn;
            assertTrue(startedOn.isVirtual(), startedOn.toString());
            assertTrue(startedOn.getName().startsWith("stride-runlevel-"), startedOn.getName());
        }
    }

    @Test
    void moreRequestsBlockAtOnceThanTheServerHasPlatformThreads() throws Exception {
        int platformThreads = 8;
        int requests = 200;
        ThreadResource.allArrived = new CountDownLatch(requests);
        ApplicationConfiguration.Builder configuration = configuration()
                .overrideProperty("services.jetty.server.config.minThreads", String.valueOf(platformThreads))
                .overrideProperty("services.jetty.server.config.maxThreads", String.valueOf(platformThreads))
                // HTTP/1.1 only, so every request has its own connection
                .overrideProperty("services.jetty.server.config.http2.enabled", "false");

        try (TestApplication application = new TestApplication.Builder().configuration(configuration).build()) {
            application.start();
            TestClient client = TestClient.create(application);

            List<Thread> callers = IntStream.range(0, requests)
                    .mapToObj(id -> Thread.ofVirtual().unstarted(() ->
                            assertEquals("true true", client.get("/thread/rendezvous?id=" + id).expect200Ok().body())))
                    .toList();
            List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();
            callers.forEach(caller -> {
                caller.setUncaughtExceptionHandler((_, e) -> failures.add(e));
                caller.start();
            });
            for (Thread caller : callers) {
                assertTrue(caller.join(java.time.Duration.ofSeconds(20)), "request did not complete");
            }

            assertEquals(List.of(), failures);
        }
    }
}
