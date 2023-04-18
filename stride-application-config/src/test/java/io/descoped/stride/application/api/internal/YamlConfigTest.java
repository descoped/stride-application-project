package io.descoped.stride.application.api.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.descoped.stride.application.api.utils.ClassPathResourceUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class YamlConfigTest {

    private static final Logger log = LoggerFactory.getLogger(YamlConfigTest.class);

    @Test
    void traverseAppYaml() throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String res = ClassPathResourceUtils.readResource("app-config.yaml");
        JsonNode root = mapper.readTree(res);
        //log.trace("config:\n{}", root.toPrettyString());

        JavaPropsMapper propsMapper = new JavaPropsMapper();
        String props = propsMapper.writeValueAsString(root);
        log.trace("props:\n{}", props);
        Set<String> propsHierachySet = new ApplicationJson(props).keys("services");
        log.trace("p: {}", new TreeSet<>(propsHierachySet));

        Set<String> yamlHierachySet = new ApplicationJson(root).keys("services");
        log.trace("y: {}", new TreeSet<>(yamlHierachySet));

        assertEquals(propsHierachySet, yamlHierachySet);
    }
}
