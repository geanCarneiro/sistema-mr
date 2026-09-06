package br.com.geangc.sistema_mr.agent.context;

import br.com.geangc.sistema_mr.memory.service.SemanticMemoryService;
import br.com.geangc.sistema_mr.state.model.CurrentStateProjection;
import br.com.geangc.sistema_mr.state.service.DynamicStateService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ContextSnapshotService implements ContextSnapshotProvider {
    private static final int STATE_LIMIT = 12;
    private static final int MEMORY_LIMIT = 6;
    private final DynamicStateService dynamicStateService;
    private final SemanticMemoryService semanticMemoryService;

    public ContextSnapshotService(DynamicStateService dynamicStateService, SemanticMemoryService semanticMemoryService) {
        this.dynamicStateService = dynamicStateService;
        this.semanticMemoryService = semanticMemoryService;
    }

    @Override
    public ContextSnapshot snapshot(UUID subjectId, String conversationId, String ownerSubject, String prompt) {
        List<CurrentStateProjection> states = dynamicStateService.currentProjections(ownerSubject, conversationId, STATE_LIMIT);
        List<CurrentStateProjection> pending = states.stream().filter(state -> isPending(state.type())).toList();
        List<CurrentStateProjection> active = states.stream().filter(state -> !isPending(state.type())).toList();
        return new ContextSnapshot(subjectId, conversationId, active,
                pending, semanticMemoryService.findRelevant(ownerSubject, conversationId, prompt, MEMORY_LIMIT));
    }

    private static boolean isPending(String type) {
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return normalized.contains("task") || normalized.contains("pending")
                || normalized.contains("pendencia") || normalized.contains("inquiry");
    }
}
