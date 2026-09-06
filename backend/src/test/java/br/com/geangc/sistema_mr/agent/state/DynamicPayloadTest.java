package br.com.geangc.sistema_mr.agent.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DynamicPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void flattensFlexiblePayloadIntoStructuralPaths() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Consulta");
        payload.put("details", Map.of("city", "São Paulo", "tags", List.of("saúde", "retorno")));

        Map<String, String> fields = DynamicPayload.fields(payload, objectMapper);

        assertEquals("\"Consulta\"", fields.get("/name"));
        assertEquals("\"São Paulo\"", fields.get("/details/city"));
        assertEquals("\"retorno\"", fields.get("/details/tags/1"));
        assertEquals(7, fields.size());
    }

    @Test
    void acceptsPropertyRemovalWithoutAStaticSchema() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("keep", true);
        original.put("remove", "old");
        Map<String, String> before = DynamicPayload.fields(original, objectMapper);

        Map<String, Object> corrected = Map.of("keep", false);
        Map<String, String> after = DynamicPayload.fields(corrected, objectMapper);

        org.junit.jupiter.api.Assertions.assertTrue(before.containsKey("/remove"));
        org.junit.jupiter.api.Assertions.assertFalse(after.containsKey("/remove"));
        assertEquals("false", after.get("/keep"));
    }

    @Test
    void requiresJsonPointerPathsForProjections() {
        assertThrows(IllegalArgumentException.class,
                () -> DynamicPayload.normalizePaths(List.of("details.city")));
    }
}
