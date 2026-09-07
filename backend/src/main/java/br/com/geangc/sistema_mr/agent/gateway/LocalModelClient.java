package br.com.geangc.sistema_mr.agent.gateway;

import br.com.geangc.sistema_mr.configuration.LocalAiProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class LocalModelClient implements LocalModelProvider {

    private final RestClient client;
    private final LocalAiProperties properties;
    private final ObjectMapper objectMapper;

    public LocalModelClient(LocalAiProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));
        this.client = RestClient.builder()
                .baseUrl(properties.serviceUrl())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LocalDecision decide(String prompt) {
        Map<String, Object> response = post("/decision", Map.of(
                "prompt", prompt,
                "maxTokens", properties.decisionMaxTokens()
        ));
        Map<?, ?> decision = map(response.get("decision"), "decision");
        return new LocalDecision(
                text(decision.get("intent"), "CLARIFY"),
                number(decision.get("confidence")),
                text(decision.get("explanation"), "A intenção não ficou clara")
        );
    }

    @Override
    public LocalVision vision(Path path, String mimeType, String prompt) {
        try {
            Map<String, Object> response = post("/vision", Map.of(
                    "mimeType", mimeType,
                    "contentBase64", Base64.getEncoder().encodeToString(Files.readAllBytes(path)),
                    "prompt", prompt,
                    "maxTokens", properties.visionMaxTokens()
            ));
            return new LocalVision(text(response.get("content"), ""));
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler o arquivo para a visão local", exception);
        }
    }

    @Override
    public LocalChat chat(
            List<Message> messages,
            List<ToolCallback> tools,
            Map<String, Object> toolContext,
            int maxTokens
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", messages.stream().map(this::messagePayload).toList());
        payload.put("maxTokens", maxTokens);
        payload.put("temperature", 0.2);
        payload.put("tools", tools.stream().map(this::toolPayload).toList());
        payload.put("toolContext", toolContext == null ? Map.of() : toolContext);

        Map<String, Object> response = post("/chat", payload);
        Map<?, ?> choice = firstChoice(response);
        Map<?, ?> message = map(choice.get("message"), "message");
        List<LocalToolCall> calls = parseToolCalls(message.get("tool_calls"));
        return new LocalChat(text(message.get("content"), ""), calls);
    }

    private Map<String, Object> post(String path, Map<String, Object> payload) {
        Map<?, ?> response;
        try {
            byte[] requestBody = objectMapper.writeValueAsBytes(payload);
            response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(requestBody.length)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (JacksonException | RestClientException exception) {
            throw new ModelCapacityException("O serviço local não está disponível", exception);
        }
        if (response == null) {
            throw new ModelCapacityException("O serviço local não retornou uma resposta", null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) response;
        return typed;
    }

    private Map<String, Object> toolPayload(ToolCallback callback) {
        var definition = callback.getToolDefinition();
        Map<String, Object> function = new HashMap<>();
        function.put("name", definition.name());
        function.put("description", definition.description());
        try {
            function.put("parameters", objectMapper.readValue(definition.inputSchema(), Map.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Schema inválido para a ferramenta " + definition.name(), exception);
        }
        return Map.of("type", "function", "function", function);
    }

    private Map<String, Object> messagePayload(Message message) {
        String role;
        if (message instanceof SystemMessage) {
            role = "system";
        } else if (message instanceof UserMessage) {
            role = "user";
        } else if (message instanceof ToolResponseMessage) {
            role = "tool";
        } else if (message instanceof AssistantMessage) {
            role = "assistant";
        } else {
            role = "user";
        }
        Map<String, Object> result = new HashMap<>();
        result.put("role", role);
        result.put("content", message.getText() == null ? "" : message.getText());
        if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
            result.put("tool_calls", assistant.getToolCalls().stream().map(call -> Map.of(
                    "id", call.id(),
                    "type", call.type(),
                    "function", Map.of("name", call.name(), "arguments", call.arguments())
            )).toList());
        }
        if (message instanceof ToolResponseMessage toolResponse && !toolResponse.getResponses().isEmpty()) {
            var response = toolResponse.getResponses().getFirst();
            result.put("tool_call_id", response.id());
        }
        return result;
    }

    private static Map<?, ?> firstChoice(Map<String, Object> response) {
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            throw new ModelCapacityException("O serviço local retornou uma resposta sem choices", null);
        }
        return map(list.getFirst(), "choice");
    }

    private static List<LocalToolCall> parseToolCalls(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<LocalToolCall> calls = new ArrayList<>();
        for (Object item : list) {
            Map<?, ?> call = map(item, "tool_call");
            Map<?, ?> function = map(call.get("function"), "tool_call.function");
            calls.add(new LocalToolCall(
                    text(call.get("id"), "local-tool-call"),
                    text(function.get("name"), ""),
                    text(function.get("arguments"), "{}")
            ));
        }
        return calls;
    }

    private static Map<?, ?> map(Object value, String name) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ModelCapacityException("Resposta local sem " + name, null);
        }
        return map;
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
