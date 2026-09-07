package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.agent.model.AgentRunCommand;
import br.com.geangc.sistema_mr.agent.model.AgentRunResult;
import br.com.geangc.sistema_mr.agent.model.AgentRunTrigger;
import br.com.geangc.sistema_mr.agent.service.AgentRuntime;
import br.com.geangc.sistema_mr.agent.service.AgentRunUnavailableException;
import br.com.geangc.sistema_mr.agent.tool.AgentToolRegistry;
import br.com.geangc.sistema_mr.controller.dto.GroundingFileDto;
import br.com.geangc.sistema_mr.privacy.PrivacyConsentInterpreter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ChatApplicationService {

    private final ConversationScopeService conversationScopeService;
    private final GroundingContextService groundingContextService;
    private final InteractionService interactionService;
    private final AgentRuntime agentRuntime;
    private final ChatMemory chatMemory;
    private final AgentToolRegistry toolRegistry;
    private final String systemInstruction;
    private final Executor agentTaskExecutor;
    private final ChatRunTracker chatRunTracker;
    private final PrivacyConsentInterpreter privacyConsentInterpreter;

    public ChatApplicationService(
            ConversationScopeService conversationScopeService,
            GroundingContextService groundingContextService,
            InteractionService interactionService,
            AgentRuntime agentRuntime,
            ChatMemory chatMemory,
            AgentToolRegistry toolRegistry,
            @Qualifier("chatSystemInstruction") String systemInstruction
    ) {
        this(conversationScopeService, groundingContextService, interactionService, agentRuntime,
                chatMemory, toolRegistry, systemInstruction, Runnable::run, new ChatRunTracker(),
                new PrivacyConsentInterpreter(null));
    }

    @Autowired
    public ChatApplicationService(
            ConversationScopeService conversationScopeService,
            GroundingContextService groundingContextService,
            InteractionService interactionService,
            AgentRuntime agentRuntime,
            ChatMemory chatMemory,
            AgentToolRegistry toolRegistry,
            @Qualifier("chatSystemInstruction") String systemInstruction,
            @Qualifier("agentTaskExecutor") Executor agentTaskExecutor,
            ChatRunTracker chatRunTracker,
            PrivacyConsentInterpreter privacyConsentInterpreter
    ) {
        this.conversationScopeService = conversationScopeService;
        this.groundingContextService = groundingContextService;
        this.interactionService = interactionService;
        this.agentRuntime = agentRuntime;
        this.chatMemory = chatMemory;
        this.toolRegistry = toolRegistry;
        this.systemInstruction = systemInstruction;
        this.agentTaskExecutor = agentTaskExecutor;
        this.chatRunTracker = chatRunTracker;
        this.privacyConsentInterpreter = privacyConsentInterpreter;
    }

    public ChatStart start(
            String prompt,
            List<UUID> attachmentIds,
            boolean includeRelatedFiles,
            UUID subjectId,
            String ownerSubject
    ) {
        UUID runId = UUID.randomUUID();
        String acknowledgement = acknowledgement(prompt, attachmentIds);
        chatRunTracker.start(runId, ownerSubject, acknowledgement);
        agentTaskExecutor.execute(() -> {
            try {
                chatRunTracker.update(runId, ownerSubject, "PREPARING_CONTEXT", preparingMessage(prompt, attachmentIds));
                ChatResult result = executeChat(
                        runId, prompt, attachmentIds, includeRelatedFiles, subjectId, ownerSubject);
                chatRunTracker.complete(
                        runId,
                        ownerSubject,
                        result,
                        result.status() == br.com.geangc.sistema_mr.agent.model.AgentRunStatus.WAITING_FOR_USER
                                ? "WAITING_FOR_USER" : "COMPLETED",
                        result.content()
                );
            } catch (AgentRunUnavailableException exception) {
                chatRunTracker.update(
                        runId, ownerSubject, "WAITING_FOR_CAPACITY", "WAITING_FOR_CAPACITY", exception.getMessage());
            } catch (RuntimeException exception) {
                chatRunTracker.fail(runId, ownerSubject, safeMessage(exception));
            }
        });
        return new ChatStart(runId, acknowledgement, Instant.now());
    }

    public ChatResult chat(
            String prompt,
            List<UUID> attachmentIds,
            boolean includeRelatedFiles,
            UUID subjectId,
            String ownerSubject
    ) {
        return executeChat(UUID.randomUUID(), prompt, attachmentIds, includeRelatedFiles, subjectId, ownerSubject);
    }

    private ChatResult executeChat(
            UUID runId,
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
        List<Message> previousMessages = chatMemory.get(scope.conversationId()).stream()
                .map(ChatMemoryMessageFormatter::messageForModel)
                .toList();
        messages.addAll(previousMessages);
        messages.add(currentUserMessage(
                prepared.modelPrompt(),
                createdAt,
                interactionId,
                userMessageId
        ));

        String privacyMode = prepared.privacyDecision().mode().name();
        if (awaitingPrivacyConsent(previousMessages)) {
            PrivacyConsentInterpreter.Consent consent = privacyConsentInterpreter.interpret(prompt);
            privacyMode = switch (consent.intent()) {
                case "ALLOW_FULL" -> prepared.privacyDecision().highestSensitivity()
                        == br.com.geangc.sistema_mr.model.DocumentSensitivity.NORMAL
                        ? "CLOUD_FULL" : "CLOUD_MINIMIZED";
                case "ALLOW_MINIMIZED" -> "CLOUD_MINIMIZED";
                default -> "LOCAL_ONLY";
            };
        }

        AgentRunResult result = agentRuntime.execute(new AgentRunCommand(
                runId,
                scope.subjectId(),
                scope.subjectTitle(),
                scope.conversationId(),
                ownerSubject,
                AgentRunTrigger.USER_MESSAGE,
                messages,
                toolRegistry.all(),
                new br.com.geangc.sistema_mr.agent.model.DataConstraints(
                        privacyMode,
                        "responder à solicitação do usuário"
                )
        ));

        if (!result.completed() && result.status() != br.com.geangc.sistema_mr.agent.model.AgentRunStatus.WAITING_FOR_USER) {
            if (result.status().name().startsWith("WAITING")) {
                throw new AgentRunUnavailableException(
                        result.failureReason() == null
                                ? "A execução aguarda capacidade para continuar"
                                : result.failureReason());
            }
            throw new IllegalStateException(
                    result.failureReason() == null ? "A execução do agente falhou" : result.failureReason());
        }
        if (result.content() == null || result.content().isBlank()) {
            throw new IllegalStateException("A execução do agente não retornou uma mensagem para o usuário");
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
                , result.status()
        );
    }

    private static String acknowledgement(String prompt, List<UUID> attachmentIds) {
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            return "Recebi os arquivos. Vou analisá-los com cuidado.";
        }
        if (prompt != null && prompt.contains("?")) {
            return "Entendi a sua pergunta. Vou verificar isso agora.";
        }
        return "Entendi. Vou cuidar disso agora.";
    }

    private static boolean awaitingPrivacyConsent(List<Message> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof AssistantMessage assistant) {
                String text = assistant.getText();
                return text != null && (text.contains("Posso") || text.contains("posso")
                        || text.contains("autoriza") || text.contains("autorizar"));
            }
            if (messages.get(index) instanceof UserMessage) {
                return false;
            }
        }
        return false;
    }

    private static String preparingMessage(String prompt, List<UUID> attachmentIds) {
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            return "Estou preparando o contexto dos arquivos selecionados…";
        }
        return "Estou entendendo o contexto da sua mensagem…";
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Não foi possível concluir esta execução."
                : message;
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
            List<GroundingFileDto> groundingFiles,
            br.com.geangc.sistema_mr.agent.model.AgentRunStatus status
    ) {}

    public record ChatStart(UUID runId, String acknowledgement, Instant timestamp) {}
}
