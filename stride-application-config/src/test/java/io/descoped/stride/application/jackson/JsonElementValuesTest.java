package io.descoped.stride.application.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading typed values out of a configuration document: a value may be a JSON number or boolean, or text that
 * holds one, as every value read from a properties file or an environment variable is.
 */
class JsonElementValuesTest {

    private static JsonElement document() {
        try {
            return JsonElement.ofStrict(new ObjectMapper().readTree("""
                    {
                      "n": {"whole": 7, "wholeText": "42", "big": 9000000000, "fraction": 1.5,
                            "fractionText": "2.25", "notANumber": "seven"},
                      "b": {"yes": true, "yesText": "true", "noText": "false", "other": "maybe"},
                      "list": ["a", "b"]
                    }
                    """));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void wholeNumbersAreReadFromNumbersAndFromText() {
        JsonElement document = document();

        assertEquals(7, document.asInt("n.whole", -1));
        assertEquals(42, document.asInt("n.wholeText", -1));
        assertEquals(9_000_000_000L, document.asLong("n.big", -1L));
        assertEquals(42L, document.asLong("n.wholeText", -1L));
    }

    @Test
    void fractionsAreReadFromNumbersAndFromText() {
        JsonElement document = document();

        assertEquals(1.5f, document.asFloat("n.fraction", -1f));
        assertEquals(2.25f, document.asFloat("n.fractionText", -1f));
        assertEquals(1.5d, document.asDouble("n.fraction", -1d));
        assertEquals(2.25d, document.asDouble("n.fractionText", -1d));
    }

    @Test
    void aValueThatIsNotANumberOrIsMissingGivesTheDefault() {
        JsonElement document = document();

        for (String key : new String[]{"n.notANumber", "n.missing"}) {
            assertEquals(-1, document.asInt(key, -1), key);
            assertEquals(-1L, document.asLong(key, -1L), key);
            assertEquals(-1f, document.asFloat(key, -1f), key);
            assertEquals(-1d, document.asDouble(key, -1d), key);
        }
    }

    @Test
    void booleansAreReadFromBooleansAndFromTextAndOnlyTrueIsTrue() {
        JsonElement document = document();

        assertTrue(document.asBoolean("b.yes", false));
        assertTrue(document.asBoolean("b.yesText", false));
        assertFalse(document.asBoolean("b.noText", true));
        assertFalse(document.asBoolean("b.other", true), "text other than true is false, as Boolean.parseBoolean has it");
        assertTrue(document.asBoolean("b.missing", true));
        assertFalse(document.asBoolean("b.missing", false));
    }

    @Test
    void aRequiredValueThatIsMissingOrMalformedFailsNamingTheKey() {
        JsonElement document = document();

        assertEquals(7, document.requireInt("n.whole"));
        assertTrue(document.requireBoolean("b.yes"));
        for (String key : new String[]{"n.notANumber", "n.missing"}) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> document.requireInt(key));
            assertTrue(failure.getMessage().startsWith("Configuration key '" + key + "' is missing or malformed"),
                    failure.getMessage());
        }
    }

    @Test
    void presenceCanBeAskedWithoutReading() {
        JsonElement document = document();

        assertTrue(document.has("n"));
        assertFalse(document.has("absent"));
        assertTrue(document.get("list").isPresent());
        assertTrue(document.get("absent").isEmpty());
        assertFalse(document.with("n").isEmpty());
        assertTrue(document.with("absent").isEmpty());
    }

    @Test
    void anArrayIsReadByPosition() {
        JsonElement list = document().with("list");

        assertEquals("a", list.at(0).asString(null));
        assertEquals("b", list.at(1).asString(null));
        assertEquals("none", list.at(2).asString("none"));
    }

    @Test
    void anObjectFlattensToDottedKeys() {
        ObjectNode numbers = document().with("n").object();
        ObjectNode root = (ObjectNode) document().json();

        // the array is left out: flattening is for objects of values, and an array has no dotted keys
        Map<String, String> flat = new LinkedHashMap<>();
        JsonElement.toFlattenedMap(flat, "", (ObjectNode) root.deepCopy().without("list"), JsonNode::asText);

        assertEquals("7", flat.get("n.whole"));
        assertEquals("true", flat.get("b.yes"));
        assertEquals(10, flat.size());
        assertEquals(Set.of("whole", "wholeText", "big", "fraction", "fractionText", "notANumber"),
                JsonElement.asMap(numbers).keySet());
        assertEquals(numbers.get("whole"), JsonElement.asMap(numbers).get("whole"));
    }
}
