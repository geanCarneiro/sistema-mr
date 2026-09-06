package br.com.geangc.sistema_mr.memory.tool;

import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;

record MemoryToolContext(String ownerSubject, String contextId, String subjectId, UUID runId) {
    static MemoryToolContext from(ToolContext context) {
        if (context == null || context.getContext() == null) {
            throw new IllegalArgumentException("O contexto da ferramenta de memória é obrigatório");
        }
        Map<String, Object> values = context.getContext();
        return new MemoryToolContext(
                required(values, "ownerSubject"), required(values, "conversationId"),
                required(values, "subjectId"), UUID.fromString(required(values, "runId"))
        );
    }

    private static String required(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("O contexto da ferramenta não contém " + key);
        }
        return value.toString();
    }
}
