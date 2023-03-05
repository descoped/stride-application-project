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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static io.descoped.stride.application.support.PropertyMapToJsonConverter.*;
import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PropertyMapToJsonConverterTest {

    private static final Logger log = LoggerFactory.getLogger(PropertyMapToJsonConverterTest.class);

    @Test
    void testJacksonDataformatProperties() throws IOException {
        ApplicationProperties properties = ApplicationProperties.builder()
                .classpathPropertiesFile("application-test.properties")
                .testDefaults()
                .build();

        Map<String, String> metadata = properties.subMap("metadata");

        long past = System.currentTimeMillis();
        JavaPropsMapper mapper = new JavaPropsMapper();
        JsonNode root = mapper.readMapAs(metadata, JsonNode.class);
        log.trace("Jackson conversion time: {}ms", System.currentTimeMillis() - past);
        //log.info("{}", root.toPrettyString());

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        //log.info("{}", yamlMapper.writeValueAsString(root));
    }

    @Test
    void loadPropertiesAndConvertToJson() {
        ApplicationProperties config = ApplicationProperties.builder()
                .classpathPropertiesFile("application-test.properties")
                .build();

        long past = System.currentTimeMillis();
        Map<String, String> metadata = config.subMap("metadata");
        PropertyMapToJsonConverter converter = new PropertyMapToJsonConverter(metadata);
        ObjectNode json = converter.json();
        log.trace("Conversion time: {}ms", System.currentTimeMillis() - past);
        //log.info("\n{}", json.toPrettyString());

        AtomicInteger assertionCount = new AtomicInteger();
        Consumer<JacksonAssert> incAssertionCounter = jsonAssert -> assertionCount.incrementAndGet();

        Jackson.of(json).with("a").assertion().arrayCount(0);
        Jackson.of(json).with("a").assertion().equalTo("v1").call(incAssertionCounter);

        Jackson.of(json).with("b").assertion().arrayCount(0);
        Jackson.of(json).with("b").assertion().equalTo("v1").call(incAssertionCounter);

        Jackson.of(json).with("c").assertion().arrayCount(4);

        Jackson.of(json).with("c").at(0).assertion().equalTo("v1").call(incAssertionCounter);

        Jackson.of(json).with("c").at(1).assertion().equalTo("v2").call(incAssertionCounter);

        Jackson.of(json).with("c").at(2).with("prop1").assertion().equalTo("v1").call(incAssertionCounter);
        Jackson.of(json).with("c").at(2).with("prop2").assertion().equalTo("v1").call(incAssertionCounter);

        Jackson.of(json).with("c").at(3).with("prop3").assertion().arrayCount(2);
        Jackson.of(json).with("c").at(3).with("prop3").assertion().arrayEquals("v1", "v2").call(incAssertionCounter, 2);

        Jackson.of(json).with("d").assertion().arrayCount(2);

        Jackson.of(json).with("d").at(0).with("prop1").at(0).with("foo").assertion().equalTo("v1").call(incAssertionCounter);
        Jackson.of(json).with("d").at(0).with("prop1").at(0).with("bar").assertion().equalTo("v2").call(incAssertionCounter);

        Jackson.of(json).with("d").at(0).with("prop2").at(0).with("bar").assertion().equalTo("v1").call(incAssertionCounter);

        Jackson.of(json).with("d").at(1).with("prop3").at(0).with("obj").with("bar").assertion().equalTo("foo").call(incAssertionCounter);
        Jackson.of(json).with("d").at(1).with("prop3").at(0).with("obj").with("foo").assertion().equalTo("bar").call(incAssertionCounter);
        Jackson.of(json).with("d").at(1).with("prop3").at(0).with("obj").with("foobar").assertion().equalTo("foobar").call(incAssertionCounter);

        Jackson.of(json).with("e").assertion().arrayCount(0);

        Jackson.of(json).with("e").with("foo").assertion().equalTo("bar").call(incAssertionCounter);
        Jackson.of(json).with("e").with("bar").assertion().equalTo("foo").call(incAssertionCounter);
        Jackson.of(json).with("e").with("foobar").assertion().equalTo("foobaz").call(incAssertionCounter);

        assertEquals(metadata.size(), assertionCount.get());
    }

    interface JacksonAssert {
        JsonNode json();

        default JacksonAssert call(Consumer<JacksonAssert> callback) {
            call(callback, 1);
            return this;
        }

        default JacksonAssert call(Consumer<JacksonAssert> callback, int repetitions) {
            IntStream.range(0, repetitions).boxed().forEach(ignore -> callback.accept(this));
            return this;
        }

        default JacksonAssert arrayCount(int exceptedSize) {
            assertEquals(exceptedSize, json() instanceof ArrayNode ? ofNullable(json()).map(JsonNode::size).orElse(0) : 0);
            return this;
        }

        default JacksonAssert arrayEquals(Object... excepted) {
            if (!(json() instanceof ArrayNode arrayNode)) {
                throw new IllegalArgumentException("Not an array-node!");
            }
            List<Object> elements = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                elements.add(arrayNode.get(i).textValue());
            }
            assertArrayEquals(excepted, elements.toArray(new Object[0]));
            return this;
        }

        default JacksonAssert equalTo(String expected) {
            assertEquals(expected, json().asText());
            return this;
        }
    }

    interface Jackson {
        JsonNode json();

        default Jackson with(String name) {
            return Jackson.of(ofNullable(json()).map(node -> node.get(name)).orElseThrow(() -> new IllegalArgumentException("Node for " + name + " NOT found!")));
        }

        default Jackson at(int index) {
            return Jackson.of(ofNullable(json()).map(node -> node.get(index)).orElseThrow(() -> new IllegalArgumentException("Node at index " + index + " NOT found!")));
        }

        default Optional<ObjectNode> toObject() {
            return to(ObjectNode.class);
        }

        default Optional<ArrayNode> toArray() {
            return to(ArrayNode.class);
        }

        default <R extends JsonNode> Optional<R> to(Class<R> clazz) {
            Objects.requireNonNull(json());
            if (json().getClass().isAssignableFrom(clazz)) {
                return ofNullable(clazz.cast(json()));
            }
            throw new ClassCastException("Excepted type is " + clazz + ", but was " + json().getClass().getName());
        }

        default JacksonAssert assertion() {
            return new FluentJacksonAssert(json());
        }

        static Jackson of(JsonNode json) {
            return new FluentJackson(json);
        }
    }


    record FluentJackson(JsonNode json) implements Jackson {
    }

    record FluentJacksonAssert(JsonNode json) implements JacksonAssert {
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
