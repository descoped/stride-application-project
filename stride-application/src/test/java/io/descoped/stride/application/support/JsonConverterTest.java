package io.descoped.stride.application.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import no.cantara.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static io.descoped.stride.application.support.PropertyMapToJsonConverter.*;
import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonConverterTest {

    private static final Logger log = LoggerFactory.getLogger(JsonConverterTest.class);

    @Test
    void testJacksonDataformatProperties() throws IOException {
        ApplicationProperties properties = ApplicationProperties.builder()
                .classpathPropertiesFile("application-test.properties")
                .testDefaults()
                .build();

        Map<String, String> metadata = properties.subMap("metadata");

        JavaPropsMapper mapper = new JavaPropsMapper();
        JsonNode root = mapper.readMapAs(metadata, JsonNode.class);
        log.info("{}", root.toPrettyString());

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        log.info("{}", yamlMapper.writeValueAsString(root));
    }

    @Test
    void loadPropertiesAndConvertToJson() {
        ApplicationProperties config = ApplicationProperties.builder()
                .classpathPropertiesFile("application-test.properties")
                .build();

        PropertyMapToJsonConverter converter = new PropertyMapToJsonConverter(config.subMap("metadata"));
        ObjectNode json = converter.json();
        //log.info("\n{}", json.toPrettyString());
        assertEquals("v1", json.get("a").asText());
        assertEquals("v1", json.get("b").asText());
        ArrayNode cArrayNode = (ArrayNode) json.get("c");
        assertEquals("v1", cArrayNode.get(0).asText());
        assertEquals("v2", cArrayNode.get(1).asText());
        assertEquals("v1", cArrayNode.get(2).get("prop1").asText());
        assertEquals("v1", cArrayNode.get(2).get("prop2").asText());
        JsonNode prop3ArrayNodeNodeWithArrayElements = cArrayNode.get(3).get("prop3");
        assertEquals("v1", prop3ArrayNodeNodeWithArrayElements.get(0).asText());
        assertEquals("v2", prop3ArrayNodeNodeWithArrayElements.get(1).asText());
        ArrayNode dArrayNode = (ArrayNode) json.get("d");
        ArrayNode prop1ArrayNodeNodeWithArrayObjects = (ArrayNode) dArrayNode.get(0).get("prop1");
        assertEquals("v1", prop1ArrayNodeNodeWithArrayObjects.get(0).get("foo").asText());
        assertEquals("v2", prop1ArrayNodeNodeWithArrayObjects.get(0).get("bar").asText());
        ArrayNode dArrayNodeWithArrayObjectProp2 = (ArrayNode) dArrayNode.get(0).get("prop2");
        assertEquals("v1", dArrayNodeWithArrayObjectProp2.get(0).get("bar").asText());
    }


    @Test
    void testTokenizer() {
        {
            String[] propertyAndValue = {"a", "v"};

            Property property = PropertyTokenizer.tokenize(propertyAndValue[0], propertyAndValue[1]);
            assertEquals(1, property.elements().size());

            Iterator<PropertyElement> elementIterator = property.elements().iterator();

            Optional<PropertyElement> propertyElement = ofNullable(elementIterator.next());
            assertEquals("a", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.LEAF_NODE, propertyElement.map(PropertyElement::type).orElse(null));

            assertEquals("v", property.value());
        }

        {
            String[] propertyWithObjectAndValue = {"a.b", "v"};

            Property property = PropertyTokenizer.tokenize(propertyWithObjectAndValue[0], propertyWithObjectAndValue[1]);
            assertEquals(2, property.elements().size());

            Iterator<PropertyElement> elementIterator = property.elements().iterator();

            Optional<PropertyElement> propertyElement = ofNullable(elementIterator.next());
            assertEquals("a", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.OBJECT, propertyElement.map(PropertyElement::type).orElse(null));

            propertyElement = ofNullable(elementIterator.next());
            assertEquals("b", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.LEAF_NODE, propertyElement.map(PropertyElement::type).orElse(null));

            assertEquals("v", property.value());
        }

        {
            String[] propertyWithObjectAndArrayValues = {"a.c.0", "v1", "a.c.1", "v2"};

            {
                Property property = PropertyTokenizer.tokenize(propertyWithObjectAndArrayValues[0], propertyWithObjectAndArrayValues[1]);
                assertEquals(3, property.elements().size());

                Iterator<PropertyElement> elementIterator = property.elements().iterator();

                Optional<PropertyElement> propertyElement = ofNullable(elementIterator.next());
                assertEquals("a", propertyElement.map(PropertyElement::key).orElse(null));
                assertEquals(ElementType.OBJECT, propertyElement.map(PropertyElement::type).orElse(null));

                propertyElement = ofNullable(elementIterator.next());
                assertEquals("c", propertyElement.map(PropertyElement::key).orElse(null));
                assertEquals(ElementType.ARRAY_NODE, propertyElement.map(PropertyElement::type).orElse(null));

                propertyElement = ofNullable(elementIterator.next());
                assertEquals("0", propertyElement.map(PropertyElement::key).orElse(null));
                assertEquals(ElementType.ARRAY_ELEMENT, propertyElement.map(PropertyElement::type).orElse(null));

                assertEquals("v1", property.value());
            }

            {
                Property property = PropertyTokenizer.tokenize(propertyWithObjectAndArrayValues[2], propertyWithObjectAndArrayValues[3]);
                assertEquals(3, property.elements().size());

                Iterator<PropertyElement> elementIterator = property.elements().iterator();

                Optional<PropertyElement> propertyElement = ofNullable(elementIterator.next());
                assertEquals("a", propertyElement.map(PropertyElement::key).orElse(null));
                assertEquals(ElementType.OBJECT, propertyElement.map(PropertyElement::type).orElse(null));

                propertyElement = ofNullable(elementIterator.next());
                assertEquals("c", propertyElement.map(PropertyElement::key).orElse(null));
                assertEquals(ElementType.ARRAY_NODE, propertyElement.map(PropertyElement::type).orElse(null));

                propertyElement = ofNullable(elementIterator.next());
                assertEquals("1", propertyElement.map(PropertyElement::key).orElse(null));
                assertEquals(ElementType.ARRAY_ELEMENT, propertyElement.map(PropertyElement::type).orElse(null));

                assertEquals("v2", property.value());
            }
        }

        String[] propertyWithObjectAndArrayObjects = {"a.d.0.p1", "v1", "a.d.0.p2", "v2"};
        {
            Property property = PropertyTokenizer.tokenize(propertyWithObjectAndArrayObjects[0], propertyWithObjectAndArrayObjects[1]);
            assertEquals(4, property.elements().size());

            Iterator<PropertyElement> elementIterator = property.elements().iterator();

            Optional<PropertyElement> propertyElement = ofNullable(elementIterator.next());
            assertEquals("a", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.OBJECT, propertyElement.map(PropertyElement::type).orElse(null));

            propertyElement = ofNullable(elementIterator.next());
            assertEquals("d", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.ARRAY_NODE, propertyElement.map(PropertyElement::type).orElse(null));

            propertyElement = ofNullable(elementIterator.next());
            assertEquals("0", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.ARRAY_OBJECT, propertyElement.map(PropertyElement::type).orElse(null));

            propertyElement = ofNullable(elementIterator.next());
            assertEquals("p1", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.LEAF_NODE, propertyElement.map(PropertyElement::type).orElse(null));

            assertEquals("v1", property.value());
        }
        {
            Property property = PropertyTokenizer.tokenize(propertyWithObjectAndArrayObjects[2], propertyWithObjectAndArrayObjects[3]);
            assertEquals(4, property.elements().size());

            Iterator<PropertyElement> elementIterator = property.elements().iterator();

            Optional<PropertyElement> propertyElement = ofNullable(elementIterator.next());
            assertEquals("a", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.OBJECT, propertyElement.map(PropertyElement::type).orElse(null));

            propertyElement = ofNullable(elementIterator.next());
            assertEquals("d", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.ARRAY_NODE, propertyElement.map(PropertyElement::type).orElse(null));

            propertyElement = ofNullable(elementIterator.next());
            assertEquals("0", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.ARRAY_OBJECT, propertyElement.map(PropertyElement::type).orElse(null));

            propertyElement = ofNullable(elementIterator.next());
            assertEquals("p2", propertyElement.map(PropertyElement::key).orElse(null));
            assertEquals(ElementType.LEAF_NODE, propertyElement.map(PropertyElement::type).orElse(null));

            assertEquals("v2", property.value());
        }
    }
}
