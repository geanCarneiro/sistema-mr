package br.com.geangc.sistema_mr.agent.gateway;

import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

public interface ToolExecutionPort {

    ToolExecutionResult execute(Prompt prompt, ChatResponse response);

    record ToolExecutionResult(List<Message> conversationHistory, boolean returnDirect) {
        public ToolExecutionResult {
            conversationHistory = conversationHistory == null ? List.of() : List.copyOf(conversationHistory);
        }
    }
}
