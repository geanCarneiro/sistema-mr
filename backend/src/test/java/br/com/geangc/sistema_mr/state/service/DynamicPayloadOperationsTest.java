package br.com.geangc.sistema_mr.state.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DynamicPayloadOperationsTest {

    @Test
    void addsNestedPropertyAndRemovesAnotherWithoutChangingOriginal() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("title", "Viagem");
        original.put("details", new LinkedHashMap<>(Map.of("city", "Recife", "days", 4)));

        Map<String, Object> result = DynamicPayloadOperations.apply(
                original,
                Map.of("details.budget", 1200, "status", "planned"),
                Set.of("details.days")
        );

        assertEquals("Viagem", result.get("title"));
        assertEquals("Recife", ((Map<?, ?>) result.get("details")).get("city"));
        assertEquals(1200, ((Map<?, ?>) result.get("details")).get("budget"));
        assertFalse(((Map<?, ?>) result.get("details")).containsKey("days"));
        assertFalse(((Map<?, ?>) original.get("details")).containsKey("budget"));
    }

    @Test
    void projectsOnlyRequestedPathsUsingNestedShape() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Viagem");
        payload.put("details", Map.of("city", "Recife", "days", 4));
        payload.put("privateNote", "não projetar");

        Map<String, Object> projection = DynamicPayloadOperations.project(
                payload,
                List.of("details.city", "title")
        );

        assertEquals(Map.of("city", "Recife"), projection.get("details"));
        assertEquals("Viagem", projection.get("title"));
        assertFalse(projection.containsKey("privateNote"));
    }
}
