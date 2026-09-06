package br.com.geangc.sistema_mr.state.service;

import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class FlexiblePayloadCodec {

    private final ObjectMapper objectMapper;
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    public FlexiblePayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> parse(String document) {
        if (document == null || document.isBlank()) {
            return Map.of();
        }
        try {
            return DynamicPayloadOperations.copyMap(objectMapper.readValue(document, Map.class));
        } catch (JacksonException jsonException) {
            Object parsed = yaml.load(document);
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("O payload YAML/JSON deve possuir um objeto na raiz", jsonException);
            }
            return DynamicPayloadOperations.copyMap(normalizeMap(map));
        }
    }

    public String write(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Payload dinâmico não serializável", exception);
        }
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), normalize(value)));
        return result;
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<Object> result = new java.util.ArrayList<>();
            iterable.forEach(item -> result.add(normalize(item)));
            return result;
        }
        return value;
    }
}
