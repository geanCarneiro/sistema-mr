/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.controller;

import br.com.geangc.sistema_mr.configuration.SanitizedNeo4jChatMemoryRepository;
import br.com.geangc.sistema_mr.controller.dto.ChatMessageDto;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author gean.carneiro
 */
@RestController
@RequestMapping("ai/chat")
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
        String prompt,
        LocalDateTime timestamp, // Opcional, se o front quiser mandar a hora local
        String conversationId
    ) {}

    // DTO de resposta
    public record ChatResponseDTO(
        String content,
        LocalDateTime timestamp,
        String messageType
    ) {}
    
    @PostMapping
    public ResponseEntity<ChatResponseDTO> chat(
            @RequestBody ChatRequestDTO request
    ) {
        // 1. Processa a chamada no ChatClients
        AssistantMessage assistantMessage = 
                chatClient.prompt()
                .user(request.prompt())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, request.conversationId))
                .call().chatResponse().getResult().getOutput();
        
        // 2. Retorna a resposta com o timestamp exato do servidor
        ChatResponseDTO responseDTO = new ChatResponseDTO(
                Optional.ofNullable(assistantMessage.getMetadata().get("rawContent").toString())
                        .orElse(null),
                Optional.ofNullable(assistantMessage.getMetadata().get("timestamp").toString())
                        .map(_ts ->  LocalDateTime.parse(_ts, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .orElse(null),
                "ASSISTANT"
        );

        return ResponseEntity.ok(responseDTO);
    }
    
    @GetMapping("/history")
    public List<ChatMessageDto> getHistory(
            @RequestParam(defaultValue = "sessao-unica-123") final String conversationId
    ) {
        return chatMemoryRepository.findByConversationId(conversationId).stream()
                .map(ChatMessageDto::fromMessage)
                .collect(Collectors.toList());
    }
    
    
}
