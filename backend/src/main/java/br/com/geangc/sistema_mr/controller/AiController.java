package br.com.geangc.sistema_mr.controller;

import br.com.geangc.sistema_mr.controller.dto.ChatMessageDto;
import br.com.geangc.sistema_mr.controller.dto.GroundingFileDto;
import br.com.geangc.sistema_mr.service.ChatApplicationService;
import br.com.geangc.sistema_mr.service.ChatHistoryService;
import br.com.geangc.sistema_mr.service.ChatRunTracker;
import br.com.geangc.sistema_mr.service.ConversationScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/chat")
public class AiController {

    private final ChatApplicationService chatApplicationService;
    private final ChatHistoryService chatHistoryService;
    private final ConversationScopeService conversationScopeService;
    private final ChatRunTracker chatRunTracker;

    public AiController(
            ChatApplicationService chatApplicationService,
            ChatHistoryService chatHistoryService,
            ConversationScopeService conversationScopeService,
            ChatRunTracker chatRunTracker
    ) {
        this.chatApplicationService = chatApplicationService;
        this.chatHistoryService = chatHistoryService;
        this.conversationScopeService = conversationScopeService;
        this.chatRunTracker = chatRunTracker;
    }

    public record ChatRequestDTO(
            @NotBlank(message = "O prompt não pode ser vazio")
            @Size(max = 32_000, message = "O prompt excede o limite de 32000 caracteres")
            String prompt,
            @Size(max = 10, message = "Selecione no máximo 10 anexos")
            List<UUID> attachmentIds,
            Boolean includeRelatedFiles,
            UUID subjectId,
            UUID privacyReviewFileId
    ) {
        public ChatRequestDTO(String prompt, List<UUID> attachmentIds, Boolean includeRelatedFiles) {
            this(prompt, attachmentIds, includeRelatedFiles, null, null);
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
            List<GroundingFileDto> groundingFiles,
            boolean privacyReviewResolved
    ) {}

    public record PrivacyReviewRequest(UUID fileId) {}

    @PostMapping
    public ResponseEntity<ChatStartDTO> chat(
            @Valid @RequestBody ChatRequestDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ChatApplicationService.ChatStart result = chatApplicationService.start(
                request.prompt(),
                request.attachmentIds(),
                request.shouldIncludeRelatedFiles(),
                request.subjectId(),
                request.privacyReviewFileId(),
                jwt.getSubject()
        );
        return ResponseEntity.accepted().body(new ChatStartDTO(
                result.runId(), result.acknowledgement(), result.timestamp()
        ));
    }

    @GetMapping("/runs/{runId}/events")
    public RunEventDTO runEvents(
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "55") long wait,
            @AuthenticationPrincipal Jwt jwt
    ) {
        long boundedWait = Math.max(0, Math.min(wait, 55));
        ChatRunTracker.RunSnapshot snapshot = chatRunTracker.await(
                runId, jwt.getSubject(), after, Duration.ofSeconds(boundedWait));
        return RunEventDTO.from(snapshot, snapshot.revision() > after);
    }

    @GetMapping("/history")
    public List<ChatMessageDto> getHistory(@AuthenticationPrincipal Jwt jwt) {
        return chatHistoryService.find(jwt.getSubject());
    }

    @PostMapping("/privacy-review")
    public ChatMessageDto requestPrivacyReview(
            @RequestBody PrivacyReviewRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return chatApplicationService.requestPrivacyReview(request.fileId(), jwt.getSubject());
    }

    @GetMapping("/subjects")
    public List<SubjectResponseDTO> getSubjects(@AuthenticationPrincipal Jwt jwt) {
        return conversationScopeService.listSubjects(jwt.getSubject()).stream()
                .map(subject -> new SubjectResponseDTO(
                        subject.id(), subject.title(), subject.kind(), subject.createdAt()))
                .toList();
    }

    public record SubjectResponseDTO(UUID id, String title, String kind, Instant createdAt) {}

    public record ChatStartDTO(UUID runId, String acknowledgement, Instant timestamp) {}

    public record RunEventDTO(
            UUID runId,
            long revision,
            String status,
            String phase,
            String message,
            ChatResponseDTO result,
            String failureReason,
            boolean terminal,
            boolean changed,
            Instant timestamp
    ) {
        static RunEventDTO from(ChatRunTracker.RunSnapshot snapshot, boolean changed) {
            ChatApplicationService.ChatResult result = snapshot.result();
            ChatResponseDTO response = result == null ? null : new ChatResponseDTO(
                    result.interactionId(), result.userMessageId(), result.assistantMessageId(),
                    result.content(), result.timestamp(), result.messageType(), result.groundingFiles(),
                    result.privacyReviewResolved());
            return new RunEventDTO(
                    snapshot.runId(), snapshot.revision(), snapshot.status(), snapshot.phase(),
                    snapshot.message(), response, snapshot.failureReason(), snapshot.terminal(),
                    changed, snapshot.timestamp());
        }
    }
}
