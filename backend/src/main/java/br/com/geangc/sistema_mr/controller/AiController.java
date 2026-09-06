package br.com.geangc.sistema_mr.controller;

import br.com.geangc.sistema_mr.controller.dto.ChatMessageDto;
import br.com.geangc.sistema_mr.controller.dto.GroundingFileDto;
import br.com.geangc.sistema_mr.service.ChatApplicationService;
import br.com.geangc.sistema_mr.service.ChatHistoryService;
import br.com.geangc.sistema_mr.service.ConversationScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat")
public class AiController {

    private final ChatApplicationService chatApplicationService;
    private final ChatHistoryService chatHistoryService;
    private final ConversationScopeService conversationScopeService;

    public AiController(
            ChatApplicationService chatApplicationService,
            ChatHistoryService chatHistoryService,
            ConversationScopeService conversationScopeService
    ) {
        this.chatApplicationService = chatApplicationService;
        this.chatHistoryService = chatHistoryService;
        this.conversationScopeService = conversationScopeService;
    }

    public record ChatRequestDTO(
            @NotBlank(message = "O prompt não pode ser vazio")
            @Size(max = 32_000, message = "O prompt excede o limite de 32000 caracteres")
            String prompt,
            @Size(max = 10, message = "Selecione no máximo 10 anexos")
            List<UUID> attachmentIds,
            Boolean includeRelatedFiles,
            UUID subjectId
    ) {
        public ChatRequestDTO(String prompt, List<UUID> attachmentIds, Boolean includeRelatedFiles) {
            this(prompt, attachmentIds, includeRelatedFiles, null);
        }

        public boolean shouldIncludeRelatedFiles() {
            return Boolean.TRUE.equals(includeRelatedFiles);
        }
    }

    public record ChatResponseDTO(
            UUID interactionId,
            UUID userMessageId,
            UUID assistantMessageId,
            String content,
            Instant timestamp,
            String messageType,
            List<GroundingFileDto> groundingFiles
    ) {}

    @PostMapping
    public ResponseEntity<ChatResponseDTO> chat(
            @Valid @RequestBody ChatRequestDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ChatApplicationService.ChatResult result = chatApplicationService.chat(
                request.prompt(),
                request.attachmentIds(),
                request.shouldIncludeRelatedFiles(),
                request.subjectId(),
                jwt.getSubject()
        );
        return ResponseEntity.ok(new ChatResponseDTO(
                result.interactionId(),
                result.userMessageId(),
                result.assistantMessageId(),
                result.content(),
                result.timestamp(),
                result.messageType(),
                result.groundingFiles()
        ));
    }

    @GetMapping("/history")
    public List<ChatMessageDto> getHistory(@AuthenticationPrincipal Jwt jwt) {
        return chatHistoryService.find(jwt.getSubject());
    }

    @GetMapping("/subjects")
    public List<SubjectResponseDTO> getSubjects(@AuthenticationPrincipal Jwt jwt) {
        return conversationScopeService.listSubjects(jwt.getSubject()).stream()
                .map(subject -> new SubjectResponseDTO(
                        subject.id(), subject.title(), subject.kind(), subject.createdAt()))
                .toList();
    }

    public record SubjectResponseDTO(UUID id, String title, String kind, Instant createdAt) {}
}
