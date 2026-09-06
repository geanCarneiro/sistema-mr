package br.com.geangc.sistema_mr.agent.context;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.memory.model.MemoryOrigin;
import br.com.geangc.sistema_mr.memory.model.MemoryRetentionPolicy;
import br.com.geangc.sistema_mr.memory.model.MemoryStatus;
import br.com.geangc.sistema_mr.memory.model.SemanticMemory;
import br.com.geangc.sistema_mr.memory.service.SemanticMemoryService;
import br.com.geangc.sistema_mr.state.model.CurrentStateProjection;
import br.com.geangc.sistema_mr.state.model.Provenance;
import br.com.geangc.sistema_mr.state.service.DynamicStateService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextSnapshotServiceTest {

    private DynamicStateService dynamicStateService;
    private SemanticMemoryService semanticMemoryService;
    private ContextSnapshotService service;

    @BeforeEach
    void setUp() {
        dynamicStateService = mock(DynamicStateService.class);
        semanticMemoryService = mock(SemanticMemoryService.class);
        service = new ContextSnapshotService(dynamicStateService, semanticMemoryService);
    }

    @Test
    void buildsExplainableSnapshotFromCurrentStatePendingTasksAndRelevantMemory() {
        UUID subjectId = UUID.randomUUID();
        CurrentStateProjection task = state("PENDING_TASK", Map.of("title", "Pagar conta"));
        CurrentStateProjection preference = state("PROFILE", Map.of("language", "pt-BR"));
        SemanticMemory memory = new SemanticMemory(
                UUID.randomUUID(), "owner", "chat-owner", subjectId.toString(), "language", "pt-BR",
                MemoryOrigin.USER_DECLARED, 1.0, List.of(new Provenance("MESSAGE", "m-1", null, null)),
                Instant.parse("2026-09-06T18:00:00Z"), null, MemoryRetentionPolicy.UNTIL_REVOKED,
                MemoryStatus.ACTIVE, 1, null, null,
                Instant.parse("2026-09-06T18:00:00Z"), Instant.parse("2026-09-06T18:00:00Z"), null);
        when(dynamicStateService.currentProjections("owner", "chat-owner", 12))
                .thenReturn(List.of(task, preference));
        when(semanticMemoryService.findRelevant("owner", "chat-owner", "qual é minha linguagem", 6))
                .thenReturn(List.of(memory));

        ContextSnapshot snapshot = service.snapshot(subjectId, "chat-owner", "owner", "qual é minha linguagem");

        assertTrue(snapshot.pendingTasks().contains(task));
        assertTrue(snapshot.relevantMemories().contains(memory));
        assertTrue(snapshot.asModelText().contains("USER_DECLARED"));
        assertTrue(snapshot.asModelText().contains("PENDING_TASK"));
        verify(dynamicStateService).currentProjections("owner", "chat-owner", 12);
        verify(semanticMemoryService).findRelevant("owner", "chat-owner", "qual é minha linguagem", 6);
    }

    private static CurrentStateProjection state(String type, Map<String, Object> payload) {
        return new CurrentStateProjection(UUID.randomUUID(), type, 2, payload,
                List.of(new Provenance("STATE", "state-1", "2", null)), 0.9,
                Instant.parse("2026-09-06T18:00:00Z"));
    }
}
