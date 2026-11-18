package io.descoped.stride.application.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonElementTest {

    private static final Logger log = LoggerFactory.getLogger(JsonElementTest.class);
    static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void findElement() throws JsonProcessingException {
        String json = """
                {"foo": "bar"}
                """;
        String name = "foo.bar";

        ObjectNode jn = mapper.readValue(json, ObjectNode.class);
        JsonElement je = JsonElement.ofStrict(jn).find(name);

        assertEquals("bar", je.asString(null));
    }

    @Test
    void dynamicWith() {
        JsonElement je = JsonElement.ofDynamic(JsonNodeFactory.instance.objectNode());
        je.with("foo.bar").object().set("a", je.object().textNode("b"));
        log.trace("\n{}", je.json().toPrettyString());

        // the dynamic strategy creates the missing nodes in the document itself
        assertEquals("{\"foo\":{\"bar\":{\"a\":\"b\"}}}", je.json().toString());
    }

    @Test
    void askingAnObjectForAnArrayOrAnArrayForAnObjectSaysWhatWasExpectedAndWhatWasThere() {
        JsonElement object = JsonElement.ofStrict(JsonNodeFactory.instance.objectNode());
        JsonElement array = JsonElement.ofStrict(JsonNodeFactory.instance.arrayNode());

        assertEquals("Expected type is com.fasterxml.jackson.databind.node.ArrayNode, "
                        + "but was com.fasterxml.jackson.databind.node.ObjectNode",
                assertThrows(ClassCastException.class, object::array).getMessage());
        assertEquals("Expected type is com.fasterxml.jackson.databind.node.ObjectNode, "
                        + "but was com.fasterxml.jackson.databind.node.ArrayNode",
                assertThrows(ClassCastException.class, array::object).getMessage());
    }
}
