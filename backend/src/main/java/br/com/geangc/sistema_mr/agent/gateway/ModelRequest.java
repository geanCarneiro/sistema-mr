package br.com.geangc.sistema_mr.agent.gateway;

import br.com.geangc.sistema_mr.agent.model.DataConstraints;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

public record ModelRequest(
        UUID runId,
        String routeId,
        List<Message> messages,
        List<ToolCallback> tools,
        Map<String, Object> toolContext,
        DataConstraints dataConstraints,
        ProviderSelectionPolicy selectionPolicy,
        int remainingSteps,
        int remainingModelInvocations,
        int remainingToolCalls
) {
    public ModelRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolContext = toolContext == null ? Map.of() : Map.copyOf(toolContext);
        dataConstraints = dataConstraints == null ? DataConstraints.unspecified() : dataConstraints;
        selectionPolicy = selectionPolicy == null ? ProviderSelectionPolicy.AUTO : selectionPolicy;
    }
}
