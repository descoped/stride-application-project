package io.descoped.stride.application.core;

import io.descoped.stride.application.api.Components;
import io.descoped.stride.application.test.server.ResponseHelper;
import io.descoped.stride.application.test.server.StrideExtension;
import io.descoped.stride.application.test.server.TestClient;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test client's request methods and the status expectations of {@link ResponseHelper}, against a real
 * application. GET, HEAD and OPTIONS are used throughout the other tests.
 */
class TestClientTest {

    @Path("/notes")
    @Produces(MediaType.TEXT_PLAIN)
    public static class NotesResource {

        @PUT
        @Path("{id}")
        public String replace(@PathParam("id") String id, String body) {
            return "put " + id + " [" + body + "]";
        }

        @POST
        public Response add(String body) {
            return Response.status(Response.Status.CREATED).entity("post [" + body + "]").build();
        }

        @POST
        @Path("json")
        @Consumes(MediaType.APPLICATION_JSON)
        public String addJson(@HeaderParam("Content-Type") String contentType, String body) {
            return contentType + " " + body;
        }

        @POST
        @Path("form")
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public String addForm(@FormParam("title") String title) {
            return "form " + title;
        }

        @DELETE
        @Path("{id}")
        public Response remove(@PathParam("id") String id) {
            return "missing".equals(id) ? Response.status(Response.Status.BAD_REQUEST).entity("no such note").build()
                    : Response.noContent().build();
        }
    }

    @RegisterExtension
    static final StrideExtension stride = StrideExtension.builder()
            .components(() -> Components.builder().resource(new NotesResource()).build())
            .build();

    @Test
    void putSendsTheBodyAndWithoutOneSendsNone(TestClient client) {
        assertEquals("put 7 [milk]", client.put("/notes/7", "milk").expect200Ok().body());
        assertEquals("put 7 []", client.put("/notes/7").expect200Ok().body());
    }

    @Test
    void postSendsTheBodyAndWithoutOneSendsNone(TestClient client) {
        assertEquals("post [eggs]", client.post("/notes", "eggs").expect201Created().body());
        assertEquals("post []", client.post("/notes").expect201Created().body());
    }

    @Test
    void postJsonAndPostFormSetTheContentType(TestClient client) {
        assertEquals("application/json {\"title\":\"tea\"}", client.postJson("/notes/json",
                HttpRequest.BodyPublishers.ofString("{\"title\":\"tea\"}"),
                HttpResponse.BodyHandlers.ofString()).expect200Ok().body());
        assertEquals("form tea", client.postForm("/notes/form",
                HttpRequest.BodyPublishers.ofString("title=tea"),
                HttpResponse.BodyHandlers.ofString()).expect200Ok().body());
    }

    @Test
    void deleteReturnsTheStatusOfTheResource(TestClient client) {
        ResponseHelper<String> removed = client.delete("/notes/7").expect204NoContent();
        assertEquals(204, removed.statusCode());

        assertEquals("no such note", client.delete("/notes/missing").expect400BadRequest().body());
    }

    @Test
    void expectAnyOfPassesWhenTheStatusIsListedAndFailsNamingTheStatusAndBodyWhenItIsNot(TestClient client) {
        ResponseHelper<String> response = client.delete("/notes/missing");

        assertEquals(400, response.expectAnyOf(404, 400).statusCode());

        AssertionError error = assertThrows(AssertionError.class, () -> response.expectAnyOf(200, 204));
        assertTrue(error.getMessage().contains("400") && error.getMessage().contains("no such note"),
                error.getMessage());
    }

    @Test
    void aStatusExpectationThatIsNotMetFailsWithTheBodyInTheMessage(TestClient client) {
        ResponseHelper<String> response = client.delete("/notes/missing");

        AssertionError error = assertThrows(AssertionError.class, response::expect204NoContent);
        assertTrue(error.getMessage().contains("no such note"), error.getMessage());
    }
}
