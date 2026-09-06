package br.com.geangc.sistema_mr.agent.gateway;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

public record ModelResponse(
        String routeId,
        String providerId,
        String modelId,
        String selectionReason,
        Prompt prompt,
        ChatResponse response
) {
    public boolean hasToolCalls() {
        return response != null && response.hasToolCalls();
    }
}
