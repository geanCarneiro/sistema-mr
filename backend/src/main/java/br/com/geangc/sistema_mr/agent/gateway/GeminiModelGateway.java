package br.com.geangc.sistema_mr.agent.gateway;

import br.com.geangc.sistema_mr.configuration.AgentRuntimeProperties;
import java.util.Locale;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class GeminiModelGateway implements ModelGateway {

    private final ChatModel chatModel;
    private final AgentRuntimeProperties properties;

    public GeminiModelGateway(ChatModel chatModel, AgentRuntimeProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public ModelResponse invoke(ModelRequest request) {
        AgentRuntimeProperties.Route route = route(request.routeId());
        if (!"google-gemini".equals(route.provider())) {
            throw new IllegalStateException("A rota não pertence ao adapter Gemini: " + route.provider());
        }
        if (request.selectionPolicy() == ProviderSelectionPolicy.LOCAL_ONLY
                || request.selectionPolicy() == ProviderSelectionPolicy.LOCAL_FIRST) {
            throw new ModelCapacityException(
                    "Não existe provider local elegível para a política " + request.selectionPolicy(), null);
        }

        if (!(chatModel.getOptions() instanceof GoogleGenAiChatOptions defaults)) {
            throw new IllegalStateException("O ChatModel configurado não usa opções Google GenAI");
        }

        GoogleGenAiChatOptions options = defaults.mutate()
                .model(resolveModel(route.model()))
                .toolCallbacks(request.tools())
                .toolContext(request.toolContext())
                .build();
        Prompt prompt = new Prompt(request.messages(), options);

        try {
            ChatResponse response = chatModel.call(prompt);
            return new ModelResponse(
                    route.id(),
                    route.provider(),
                    route.model(),
                    "ONLY_ELIGIBLE_PROVIDER",
                    prompt,
                    response
            );
        } catch (RuntimeException exception) {
            if (isCapacityFailure(exception)) {
                throw new ModelCapacityException(
                        "O provider Gemini está sem capacidade ou quota disponível", exception);
            }
            throw exception;
        }
    }

    private AgentRuntimeProperties.Route route(String routeId) {
        String selectedRoute = routeId == null || routeId.isBlank()
                ? properties.defaultRouteConfig().id()
                : routeId;
        return properties.routes().stream()
                .filter(route -> selectedRoute.equals(route.id()) && route.enabled())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Rota de modelo não encontrada: " + selectedRoute));
    }

    private static GoogleGenAiChatModel.ChatModel resolveModel(String model) {
        for (GoogleGenAiChatModel.ChatModel candidate : GoogleGenAiChatModel.ChatModel.values()) {
            if (candidate.getValue().equals(model)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Modelo Gemini não suportado pelo adapter: " + model);
    }

    private static boolean isCapacityFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toUpperCase(Locale.ROOT);
                if (normalized.contains("429")
                        || normalized.contains("RESOURCE_EXHAUSTED")
                        || normalized.contains("QUOTA")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
