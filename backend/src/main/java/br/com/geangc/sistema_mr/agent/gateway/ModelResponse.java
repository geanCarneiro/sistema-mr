package br.com.geangc.sistema_mr.agent.gateway;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.metadata.Usage;

public record ModelResponse(
        String routeId,
        String providerId,
        String modelId,
        String selectionReason,
        Prompt prompt,
        ChatResponse response,
        InvocationUsage usage
) {
    public ModelResponse(
            String routeId,
            String providerId,
            String modelId,
            String selectionReason,
            Prompt prompt,
            ChatResponse response
    ) {
        this(routeId, providerId, modelId, selectionReason, prompt, response, InvocationUsage.unknown());
    }

    public boolean hasToolCalls() {
        return response != null && response.hasToolCalls();
    }

    public record InvocationUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {

        public static InvocationUsage unknown() {
            return new InvocationUsage(null, null, null);
        }

        public static InvocationUsage from(ChatResponse response) {
            if (response == null || response.getMetadata() == null) {
                return unknown();
            }
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                return unknown();
            }
            return new InvocationUsage(
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens()
            );
        }
    }
}
