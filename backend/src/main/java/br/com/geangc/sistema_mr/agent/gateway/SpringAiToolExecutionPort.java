package br.com.geangc.sistema_mr.agent.gateway;

import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.stereotype.Component;

@Component
public class SpringAiToolExecutionPort implements ToolExecutionPort {

    private final ToolCallingManager toolCallingManager;

    public SpringAiToolExecutionPort(ToolCallingManager toolCallingManager) {
        this.toolCallingManager = toolCallingManager;
    }

    @Override
    public ToolExecutionResult execute(Prompt prompt, ChatResponse response) {
        var result = toolCallingManager.executeToolCalls(prompt, response);
        List<Message> history = result.conversationHistory();
        return new ToolExecutionResult(history, result.returnDirect());
    }
}
