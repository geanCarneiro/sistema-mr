package br.com.geangc.sistema_mr.agent.model;

import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

public record AgentRunCommand(
        UUID runId,
        UUID subjectId,
        String subjectTitle,
        String conversationId,
        String ownerSubject,
        AgentRunTrigger trigger,
        List<Message> messages,
        List<ToolCallback> tools,
        DataConstraints dataConstraints
) {
    public AgentRunCommand {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        dataConstraints = dataConstraints == null ? DataConstraints.unspecified() : dataConstraints;
    }
}
