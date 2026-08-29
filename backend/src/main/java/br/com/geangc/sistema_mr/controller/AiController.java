/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.controller;

import br.com.geangc.sistema_mr.configuration.SanitizedNeo4jChatMemoryRepository;
import br.com.geangc.sistema_mr.controller.dto.ChatMessageDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author gean.carneiro
 */
@RestController
@RequestMapping("/api/ai/chat")
public class AiController {
    
    private final ChatClient chatClient;
    private final SanitizedNeo4jChatMemoryRepository chatMemoryRepository;
    
    public AiController(
            final ChatClient chatClient,
            final SanitizedNeo4jChatMemoryRepository chatMemoryRepository
    ) {
        this.chatClient = chatClient;
        this.chatMemoryRepository = chatMemoryRepository;
    }
    
        // DTO de requisição
    public record ChatRequestDTO(
        @NotBlank(message = "O prompt não pode ser vazio")
        @Size(max = 32_000, message = "O prompt excede o limite de 32000 caracteres")
        String prompt
    ) {}

    // DTO de resposta
    public record ChatResponseDTO(
        String content,
        Instant timestamp,
        String messageType
    ) {}
    
    @PostMapping
    public ResponseEntity<ChatResponseDTO> chat(
            @Valid @RequestBody ChatRequestDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String conversationId = conversationIdFor(jwt.getSubject());

        ChatResponse chatResponse = chatClient.prompt()
                .user(request.prompt())
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();

        AssistantMessage assistantMessage = Optional.ofNullable(chatResponse)
                .map(ChatResponse::getResult)
                .map(result -> result.getOutput())
                .orElseThrow(() -> new IllegalStateException("O modelo não retornou uma resposta"));

        String content = Optional.ofNullable(assistantMessage.getMetadata().get("rawContent"))
                .map(Object::toString)
                .orElseGet(assistantMessage::getText);

        ChatResponseDTO responseDTO = new ChatResponseDTO(
                content,
                timestampFrom(assistantMessage),
                "ASSISTANT"
        );

        return ResponseEntity.ok(responseDTO);
    }
    
    @GetMapping("/history")
    public List<ChatMessageDto> getHistory(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String conversationId = conversationIdFor(jwt.getSubject());
        return chatMemoryRepository.findByConversationId(conversationId).stream()
                .map(ChatMessageDto::fromMessage)
                .collect(Collectors.toList());
    }

    static String conversationIdFor(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT sem subject");
        }
        return "chat-" + subject;
    }

    private static Instant timestampFrom(AssistantMessage message) {
        Object timestamp = message.getMetadata().get("timestamp");
        if (timestamp != null) {
            try {
                return Instant.parse(timestamp.toString());
            } catch (DateTimeParseException ignored) {
                // Respostas antigas podem conter LocalDateTime sem offset.
            }
        }
        return Instant.now();
    }
}
