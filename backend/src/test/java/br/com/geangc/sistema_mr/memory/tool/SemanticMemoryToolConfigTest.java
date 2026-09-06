package br.com.geangc.sistema_mr.memory.tool;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.geangc.sistema_mr.memory.service.SemanticMemoryService;
import br.com.geangc.sistema_mr.state.model.Provenance;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

class SemanticMemoryToolConfigTest {

    @Test
    void derivesMemoryScopeFromBackendToolContext() {
        SemanticMemoryService service = mock(SemanticMemoryService.class);
        SemanticMemoryToolConfig config = new SemanticMemoryToolConfig(service);
        UUID runId = UUID.randomUUID();
        ToolContext context = new ToolContext(Map.of(
                "ownerSubject", "owner", "conversationId", "chat-owner",
                "subjectId", "subject-1", "runId", runId.toString()));

        config.rememberSemanticFact(new SemanticMemoryToolConfig.RememberRequest(
                "timezone", "UTC", "USER_DECLARED", 1.0, null, "UNTIL_REVOKED",
                "MESSAGE", "message-1", null, "body"), context);

        verify(service).remember(
                eq("owner"), eq("chat-owner"), eq("subject-1"), eq("timezone"), eq("UTC"),
                eq("USER_DECLARED"), eq(1.0), eq(null), eq("UNTIL_REVOKED"),
                eq(new Provenance("MESSAGE", "message-1", null, "body")));
    }
}
