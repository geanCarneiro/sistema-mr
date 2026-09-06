package br.com.geangc.sistema_mr.agent.context;

import br.com.geangc.sistema_mr.memory.model.SemanticMemory;
import br.com.geangc.sistema_mr.state.model.CurrentStateProjection;
import java.util.List;
import java.util.UUID;

public record ContextSnapshot(UUID subjectId, String conversationId,
                              List<CurrentStateProjection> activeStates,
                              List<CurrentStateProjection> pendingTasks,
                              List<SemanticMemory> relevantMemories) {
    public ContextSnapshot {
        activeStates = activeStates == null ? List.of() : List.copyOf(activeStates);
        pendingTasks = pendingTasks == null ? List.of() : List.copyOf(pendingTasks);
        relevantMemories = relevantMemories == null ? List.of() : List.copyOf(relevantMemories);
    }

    public String asModelText() {
        StringBuilder text = new StringBuilder("Contexto mínimo recuperado pelo backend (não é histórico completo):\n");
        appendStates(text, "Estado ativo", activeStates);
        appendStates(text, "Tarefas pendentes", pendingTasks);
        text.append("Memórias relevantes:\n");
        if (relevantMemories.isEmpty()) {
            text.append("- nenhuma\n");
        } else {
            relevantMemories.forEach(memory -> text.append("- ").append(memory.key()).append(": ")
                    .append(memory.value()).append(" [origem=").append(memory.origin())
                    .append(", confiança=").append(memory.confidence())
                    .append(", validade=").append(memory.validUntil())
                    .append(", fonte=").append(memory.provenance()).append("]\n"));
        }
        text.append("Use as ferramentas para consultar ou alterar memórias; o snapshot não é autorização.\n");
        return text.toString();
    }

    private static void appendStates(StringBuilder text, String label, List<CurrentStateProjection> states) {
        text.append(label).append(":\n");
        if (states.isEmpty()) {
            text.append("- nenhum\n");
            return;
        }
        states.forEach(state -> text.append("- ").append(state.type()).append(" v").append(state.version())
                .append(": ").append(state.payload()).append(" [confiança=").append(state.confidence())
                .append(", fonte=").append(state.provenance()).append("]\n"));
    }
}
