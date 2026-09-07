package br.com.geangc.sistema_mr.agent.gateway;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

public interface LocalModelProvider {

    LocalDecision decide(String prompt);

    LocalVision vision(Path path, String mimeType, String prompt);

    LocalChat chat(
            List<Message> messages,
            List<ToolCallback> tools,
            Map<String, Object> toolContext,
            int maxTokens
    );

    record LocalDecision(String intent, double confidence, String explanation) {}

    record LocalVision(String content) {}

    record LocalChat(String content, List<LocalToolCall> toolCalls) {
        public LocalChat {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    record LocalToolCall(String id, String name, String arguments) {}
}
