package br.com.geangc.sistema_mr.agent.gateway;

import br.com.geangc.sistema_mr.configuration.AgentRuntimeProperties;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.stereotype.Component;

@Component
public class LocalModelGateway implements ModelGateway {

    private final LocalModelProvider provider;
    private final AgentRuntimeProperties properties;

    public LocalModelGateway(LocalModelProvider provider, AgentRuntimeProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public ModelResponse invoke(ModelRequest request) {
        AgentRuntimeProperties.Route route = properties.routes().stream()
                .filter(candidate -> candidate.id().equals(request.routeId()) && candidate.enabled())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Rota local não encontrada: " + request.routeId()));
        if (!"local-ai".equals(route.provider())) {
            throw new IllegalStateException("A rota não pertence ao provider local: " + route.provider());
        }

        LocalModelProvider.LocalChat local = provider.chat(
                request.messages(), request.tools(), request.toolContext(),
                Math.min(request.remainingSteps() * 1024, route.outputTokenLimit())
        );
        AssistantMessage message = AssistantMessage.builder()
                .content(local.content())
                .toolCalls(local.toolCalls().stream()
                        .map(call -> new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments()))
                        .toList())
                .build();
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .model(route.model())
                .toolCallbacks(request.tools())
                .toolContext(request.toolContext())
                .maxTokens(Math.min(request.remainingSteps() * 1024, route.outputTokenLimit()))
                .temperature(0.2)
                .build();
        Prompt prompt = new Prompt(request.messages(), options);
        return new ModelResponse(
                route.id(),
                route.provider(),
                route.model(),
                "LOCAL_ONLY_POLICY",
                prompt,
                new ChatResponse(List.of(new Generation(message)))
        );
    }
}
