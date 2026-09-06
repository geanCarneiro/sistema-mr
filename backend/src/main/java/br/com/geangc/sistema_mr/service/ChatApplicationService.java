package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.agent.model.AgentRunCommand;
import br.com.geangc.sistema_mr.agent.model.AgentRunResult;
import br.com.geangc.sistema_mr.agent.model.AgentRunTrigger;
import br.com.geangc.sistema_mr.agent.service.AgentRuntime;
import br.com.geangc.sistema_mr.agent.service.AgentRunUnavailableException;
import br.com.geangc.sistema_mr.controller.dto.GroundingFileDto;
import br.com.geangc.sistema_mr.tool_calling.PythonToolConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ChatApplicationService {

    private final ConversationScopeService conversationScopeService;
    private final GroundingContextService groundingContextService;
    private final InteractionService interactionService;
    private final AgentRuntime agentRuntime;
    private final ChatMemory chatMemory;
    private final PythonToolConfig pythonToolConfig;
    private final String systemInstruction;

    public ChatApplicationService(
            ConversationScopeService conversationScopeService,
            GroundingContextService groundingContextService,
            InteractionService interactionService,
            AgentRuntime agentRuntime,
            ChatMemory chatMemory,
            PythonToolConfig pythonToolConfig,
            @Qualifier("chatSystemInstruction") String systemInstruction
    ) {
        this.conversationScopeService = conversationScopeService;
        this.groundingContextService = groundingContextService;
        this.interactionService = interactionService;
        this.agentRuntime = agentRuntime;
        this.chatMemory = chatMemory;
        this.pythonToolConfig = pythonToolConfig;
        this.systemInstruction = systemInstruction;
    }

    public ChatResult chat(
            String prompt,
            List<UUID> attachmentIds,
            boolean includeRelatedFiles,
            UUID subjectId,
            String ownerSubject
    ) {
        Instant createdAt = Instant.now();
        UUID interactionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        ConversationScopeService.ConversationScope scope = conversationScopeService.resolve(ownerSubject, subjectId);

        GroundingContextService.PreparedPrompt prepared = groundingContextService.prepare(
                scope.conversationId(),
                ownerSubject,
                prompt,
                attachmentIds,
                includeRelatedFiles
        );

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemInstruction));
        chatMemory.get(scope.conversationId()).stream()
                .map(ChatMemoryMessageFormatter::messageForModel)
                .forEach(messages::add);
        messages.add(currentUserMessage(
                prepared.modelPrompt(),
                createdAt,
                interactionId,
                userMessageId
        ));

        List<ToolCallback> tools = Arrays.asList(ToolCallbacks.from(pythonToolConfig));
        AgentRunResult result = agentRuntime.execute(new AgentRunCommand(
                UUID.randomUUID(),
                scope.subjectId(),
                scope.subjectTitle(),
                scope.conversationId(),
                ownerSubject,
                AgentRunTrigger.USER_MESSAGE,
                messages,
                tools,
                null
        ));

        if (!result.completed()) {
            if (result.status().name().startsWith("WAITING")) {
                throw new AgentRunUnavailableException(
                        result.failureReason() == null
                                ? "A execução aguarda capacidade para continuar"
                                : result.failureReason());
            }
            throw new IllegalStateException(
                    result.failureReason() == null ? "A execução do agente falhou" : result.failureReason());
        }

        Instant completedAt = result.completedAt() == null ? Instant.now() : result.completedAt();
        interactionService.persistCompleted(
                interactionId,
                userMessageId,
                assistantMessageId,
                prompt,
                result.content(),
                scope.conversationId(),
                ownerSubject,
                createdAt,
                completedAt,
                prepared.files()
        );
        persistConversationMemory(
                scope.conversationId(),
                prompt,
                result.content(),
                createdAt,
                completedAt,
                interactionId,
                userMessageId,
                assistantMessageId
        );

        return new ChatResult(
                interactionId,
                userMessageId,
                assistantMessageId,
                result.content(),
                completedAt,
                "ASSISTANT",
                prepared.files().stream().map(GroundingFileDto::from).toList()
        );
    }

    private void persistConversationMemory(
            String conversationId,
            String prompt,
            String response,
            Instant createdAt,
            Instant completedAt,
            UUID interactionId,
            UUID userMessageId,
            UUID assistantMessageId
    ) {
        Map<String, Object> userMetadata = new HashMap<>();
        userMetadata.put("timestamp", createdAt.toString());
        userMetadata.put("interactionId", interactionId.toString());
        userMetadata.put("messageId", userMessageId.toString());
        chatMemory.add(conversationId, UserMessage.builder()
                .text(prompt)
                .metadata(userMetadata)
                .build());

        Map<String, Object> assistantMetadata = new HashMap<>();
        assistantMetadata.put("timestamp", completedAt.toString());
        assistantMetadata.put("interactionId", interactionId.toString());
        assistantMetadata.put("messageId", assistantMessageId.toString());
        chatMemory.add(conversationId, AssistantMessage.builder()
                .content(response)
                .properties(assistantMetadata)
                .build());
    }

    private static UserMessage currentUserMessage(
            String prompt,
            Instant createdAt,
            UUID interactionId,
            UUID userMessageId
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", createdAt.toString());
        metadata.put("interactionId", interactionId.toString());
        metadata.put("messageId", userMessageId.toString());
        return UserMessage.builder()
                .text("[" + createdAt + "] " + prompt)
                .metadata(metadata)
                .build();
    }

    public record ChatResult(
            UUID interactionId,
            UUID userMessageId,
            UUID assistantMessageId,
            String content,
            Instant timestamp,
            String messageType,
            List<GroundingFileDto> groundingFiles
    ) {}
}
