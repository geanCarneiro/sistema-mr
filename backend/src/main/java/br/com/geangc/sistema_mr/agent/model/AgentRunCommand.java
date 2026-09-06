package br.com.geangc.sistema_mr.agent.model;

import br.com.geangc.sistema_mr.agent.tool.AgentTool;
import br.com.geangc.sistema_mr.agent.tool.AutonomyLevel;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.chat.messages.Message;

public record AgentRunCommand(
        UUID runId,
        UUID subjectId,
        String subjectTitle,
        String conversationId,
        String ownerSubject,
        AgentRunTrigger trigger,
        List<Message> messages,
        List<AgentTool> tools,
        DataConstraints dataConstraints,
        AutonomyLevel grantedAutonomy,
        Set<String> permissions
) {
    public AgentRunCommand(
            UUID runId,
            UUID subjectId,
            String subjectTitle,
            String conversationId,
            String ownerSubject,
            AgentRunTrigger trigger,
            List<Message> messages,
            List<AgentTool> tools,
            DataConstraints dataConstraints
    ) {
        this(
                runId,
                subjectId,
                subjectTitle,
                conversationId,
                ownerSubject,
                trigger,
                messages,
                tools,
                dataConstraints,
                AutonomyLevel.EXECUTE,
                Set.of()
        );
    }

    public AgentRunCommand {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        dataConstraints = dataConstraints == null ? DataConstraints.unspecified() : dataConstraints;
        grantedAutonomy = grantedAutonomy == null ? AutonomyLevel.OBSERVE : grantedAutonomy;
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
