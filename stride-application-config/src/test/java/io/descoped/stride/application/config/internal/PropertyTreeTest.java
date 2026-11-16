package io.descoped.stride.application.config.internal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyTreeTest {

    private static Map<String, String> properties(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    @Test
    void nestsObjectsAndKeepsValuesAsText() {
        JsonNode json = PropertyTree.toJson(properties(
                "server.port", "8080",
                "server.host", "localhost",
                "name", "stride"));

        assertEquals("{\"server\":{\"port\":\"8080\",\"host\":\"localhost\"},\"name\":\"stride\"}", json.toString());
    }

    @Test
    void numericSegmentsBecomeArraysOrderedByIndex() {
        JsonNode json = PropertyTree.toJson(properties(
                "dispatches.2", "REQUEST",
                "dispatches.10", "ASYNC",
                "dispatches.1", "FORWARD"));

        assertEquals("{\"dispatches\":[\"FORWARD\",\"REQUEST\",\"ASYNC\"]}", json.toString());
    }

    @Test
    void arraysHoldObjectsAndNestedArrays() {
        JsonNode json = PropertyTree.toJson(properties(
                "c.0", "v1",
                "c.1.prop1", "v2",
                "c.2.prop3.0", "v3",
                "c.2.prop3.1", "v4"));

        assertEquals("{\"c\":[\"v1\",{\"prop1\":\"v2\"},{\"prop3\":[\"v3\",\"v4\"]}]}", json.toString());
    }

    @Test
    void emptyArrayElementIsLeftOutAndEmptyValueIsKept() {
        JsonNode json = PropertyTree.toJson(properties(
                "list.0", "a",
                "list.1", "",
                "blank", ""));

        assertEquals("{\"list\":[\"a\"],\"blank\":\"\"}", json.toString());
    }

    @Test
    void mixedNumericAndNamedChildrenStayAnObject() {
        JsonNode json = PropertyTree.toJson(properties(
                "a.0", "x",
                "a.name", "y"));

        assertTrue(json.get("a").isObject());
        assertEquals("x", json.get("a").get("0").asText());
    }

    @Test
    void keyThatIsBothValueAndParentIsRejectedInEitherOrder() {
        IllegalArgumentException first = assertThrows(IllegalArgumentException.class,
                () -> PropertyTree.toJson(properties("a", "1", "a.b", "2")));
        assertTrue(first.getMessage().contains("'a.b'"));

        IllegalArgumentException second = assertThrows(IllegalArgumentException.class,
                () -> PropertyTree.toJson(properties("a.b", "2", "a", "1")));
        assertTrue(second.getMessage().contains("'a'"));
    }
}
