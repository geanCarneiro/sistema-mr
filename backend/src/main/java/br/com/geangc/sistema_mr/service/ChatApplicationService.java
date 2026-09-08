package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.agent.model.AgentRunCommand;
import br.com.geangc.sistema_mr.agent.model.AgentRun;
import br.com.geangc.sistema_mr.agent.model.AgentRunResult;
import br.com.geangc.sistema_mr.agent.model.AgentRunTrigger;
import br.com.geangc.sistema_mr.agent.model.QueuedChatRequest;
import br.com.geangc.sistema_mr.agent.service.AgentRuntime;
import br.com.geangc.sistema_mr.agent.service.AgentRunUnavailableException;
import br.com.geangc.sistema_mr.agent.service.InMemoryRunScheduler;
import br.com.geangc.sistema_mr.agent.tool.AgentToolRegistry;
import br.com.geangc.sistema_mr.controller.dto.GroundingFileDto;
import br.com.geangc.sistema_mr.controller.dto.ChatMessageDto;
import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentStatus;
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
    private final InMemoryRunScheduler scheduler;
    private final DocumentService documentService;

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
                new PrivacyConsentInterpreter(null), null, null);
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
            PrivacyConsentInterpreter privacyConsentInterpreter,
            InMemoryRunScheduler scheduler,
            DocumentService documentService
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
        this.scheduler = scheduler;
        this.documentService = documentService;
    }

    public ChatStart start(
            String prompt,
            List<UUID> attachmentIds,
            boolean includeRelatedFiles,
            UUID subjectId,
            UUID privacyReviewFileId,
            String ownerSubject
    ) {
        UUID runId = UUID.randomUUID();
        String acknowledgement = acknowledgement(prompt, attachmentIds);
        chatRunTracker.start(runId, ownerSubject, acknowledgement);
        if (scheduler != null) {
            scheduler.register(new QueuedChatRequest(
                    runId, prompt, attachmentIds, includeRelatedFiles, subjectId, privacyReviewFileId));
        }
        agentTaskExecutor.execute(() -> {
            try {
                chatRunTracker.update(runId, ownerSubject, "PREPARING_CONTEXT", preparingMessage(prompt, attachmentIds));
                ChatResult result = executeChat(
                        runId, prompt, attachmentIds, includeRelatedFiles, subjectId, privacyReviewFileId, ownerSubject);
                chatRunTracker.complete(
                        runId,
                        ownerSubject,
                        result,
                        result.status() == br.com.geangc.sistema_mr.agent.model.AgentRunStatus.WAITING_FOR_USER
                                ? "WAITING_FOR_USER" : "COMPLETED",
                        result.content()
                );
                if (scheduler != null) {
                    scheduler.complete(runId);
                }
            } catch (AgentRunUnavailableException exception) {
                chatRunTracker.update(
                        runId, ownerSubject, "WAITING_FOR_CAPACITY", "WAITING_FOR_CAPACITY", exception.getMessage());
            } catch (RuntimeException exception) {
                chatRunTracker.fail(runId, ownerSubject, safeMessage(exception));
                if (scheduler != null) {
                    scheduler.complete(runId);
                }
            }
        });
        return new ChatStart(runId, acknowledgement, Instant.now());
    }

    public ChatStart start(
            String prompt,
            List<UUID> attachmentIds,
            boolean includeRelatedFiles,
            UUID subjectId,
            String ownerSubject
    ) {
        return start(prompt, attachmentIds, includeRelatedFiles, subjectId, null, ownerSubject);
    }

    /** Retoma uma solicitação de chat persistida pelo worker após um restart. */
    public void resume(AgentRun run, QueuedChatRequest request) {
        chatRunTracker.start(run.id(), run.ownerSubject(), "Retomando a execução após reinicialização.");
        try {
            ChatResult result = executeChat(
                    run.id(), request.prompt(), request.attachmentIds(), request.includeRelatedFiles(),
                    run.subjectId(), request.privacyReviewFileId(), run.ownerSubject());
            chatRunTracker.complete(
                    run.id(), run.ownerSubject(), result,
                    result.status() == br.com.geangc.sistema_mr.agent.model.AgentRunStatus.WAITING_FOR_USER
                            ? "WAITING_FOR_USER" : "COMPLETED",
                    result.content());
        } catch (AgentRunUnavailableException exception) {
            chatRunTracker.update(
                    run.id(), run.ownerSubject(), "WAITING_FOR_CAPACITY", "WAITING_FOR_CAPACITY",
                    exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            chatRunTracker.fail(run.id(), run.ownerSubject(), safeMessage(exception));
            throw exception;
        }
    }

    public ChatResult chat(
            String prompt,
            List<UUID> attachmentIds,
            boolean includeRelatedFiles,
            UUID subjectId,
            String ownerSubject
    ) {
        return executeChat(UUID.randomUUID(), prompt, attachmentIds, includeRelatedFiles, subjectId, null, ownerSubject);
    }

    private ChatResult executeChat(
            UUID runId,
            String prompt,
            List<UUID> attachmentIds,
            boolean includeRelatedFiles,
            UUID subjectId,
            UUID privacyReviewFileId,
            String ownerSubject
    ) {
        Instant createdAt = Instant.now();
        UUID interactionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        ConversationScopeService.ConversationScope scope = conversationScopeService.resolve(ownerSubject, subjectId);

        if (privacyReviewFileId != null) {
            return executePrivacyReview(
                    runId, prompt, privacyReviewFileId, scope, ownerSubject, createdAt,
                    interactionId, userMessageId, assistantMessageId);
        }

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
                .filter(message -> !isPrivacyReviewMessage(message))
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
                , result.status(), false
        );
    }

    public ChatMessageDto requestPrivacyReview(UUID fileId, String ownerSubject) {
        if (fileId == null || documentService == null) {
            throw new IllegalArgumentException("O arquivo da revisão de privacidade é obrigatório");
        }
        ConversationScopeService.ConversationScope scope = conversationScopeService.resolve(ownerSubject);
        ChatFile file = documentService.findOwned(fileId, scope.conversationId(), ownerSubject);
        if (file.status() != DocumentStatus.NEEDS_REVIEW && file.status() != DocumentStatus.FAILED) {
            throw new IllegalArgumentException("O arquivo não está aguardando revisão de privacidade");
        }

        UUID messageId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        String content = "Encontrei um problema ao analisar o arquivo " + file.originalName()
                + ". Por segurança, ele está bloqueado e não será usado nesta conversa até que a análise seja concluída."
                + (file.errorMessage() == null || file.errorMessage().isBlank()
                        ? ""
                        : " O que aconteceu: " + file.errorMessage())
                + " Você prefere que eu tente processá-lo novamente ou que eu mantenha o arquivo bloqueado?"
                + " Responda com suas próprias palavras.";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("messageId", messageId.toString());
        metadata.put("timestamp", timestamp.toString());
        metadata.put("messageKind", "PRIVACY_REVIEW");
        metadata.put("privacyFileId", fileId.toString());
        metadata.put("rawContent", content);
        AssistantMessage message = AssistantMessage.builder().content(content).properties(metadata).build();
        chatMemory.add(scope.conversationId(), message);
        return ChatMessageDto.fromMessage(message);
    }

    private ChatResult executePrivacyReview(
            UUID runId,
            String prompt,
            UUID fileId,
            ConversationScopeService.ConversationScope scope,
            String ownerSubject,
            Instant createdAt,
            UUID interactionId,
            UUID userMessageId,
            UUID assistantMessageId
    ) {
        if (documentService == null) {
            throw new IllegalStateException("O serviço de documentos não está disponível");
        }
        PrivacyConsentInterpreter.Review review = privacyConsentInterpreter.interpretReview(prompt);
        ChatFile file = documentService.findOwned(fileId, scope.conversationId(), ownerSubject);
        String response;
        boolean resolved = false;
        switch (review.intent()) {
            case "RETRY_ANALYSIS" -> {
                documentService.retry(fileId, scope.conversationId(), ownerSubject);
                response = "Entendi. Vou tentar processar novamente o arquivo " + file.originalName()
                        + ". Ele continuará bloqueado até a análise local terminar.";
                resolved = true;
            }
            case "KEEP_BLOCKED" -> {
                response = "Tudo bem. Vou manter o arquivo " + file.originalName()
                        + " bloqueado e não o usarei nesta conversa.";
                resolved = true;
            }
            default -> response = "Não consegui identificar sua decisão. Você quer que eu tente processar o arquivo novamente ou prefere mantê-lo bloqueado?";
        }

        Instant completedAt = Instant.now();
        interactionService.persistCompleted(
                interactionId, userMessageId, assistantMessageId, prompt, response,
                scope.conversationId(), ownerSubject, createdAt, completedAt, List.of());
        persistConversationMemory(
                scope.conversationId(), prompt, response, createdAt, completedAt,
                interactionId, userMessageId, assistantMessageId);
        return new ChatResult(
                interactionId, userMessageId, assistantMessageId, response, completedAt,
                "ASSISTANT", List.of(),
                br.com.geangc.sistema_mr.agent.model.AgentRunStatus.COMPLETED, resolved);
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

    private static boolean isPrivacyReviewMessage(Message message) {
        return "PRIVACY_REVIEW".equals(String.valueOf(message.getMetadata().get("messageKind")));
    }

    private static String preparingMessage(String prompt, List<UUID> attachmentIds) {
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            return "Estou preparando o contexto dos arquivos selecionados…";
        }
        return "Estou entendendo o contexto da sua mensagem…";
    }

    static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if ("RUN_CONTEXT_LIMIT_EXCEEDED".equals(message)
                || exception instanceof GroundingContextLimitException) {
            return GroundingContextLimitException.USER_MESSAGE;
        }
        if (exception instanceof GroundingEvidenceInsufficientException) {
            return GroundingEvidenceInsufficientException.USER_MESSAGE;
        }
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
            br.com.geangc.sistema_mr.agent.model.AgentRunStatus status,
            boolean privacyReviewResolved
    ) {}

    public record ChatStart(UUID runId, String acknowledgement, Instant timestamp) {}
}
