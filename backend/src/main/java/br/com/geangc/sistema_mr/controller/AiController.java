/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.controller;

import br.com.geangc.sistema_mr.controller.dto.ChatMessageDto;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
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
    private final ChatMemory chatMemory;
    
    public AiController(
            final ChatClient chatClient,
            final ChatMemory chatMemory
    ) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }
    
    @PostMapping
    public String chat(
            @RequestBody String prompt, 
            @RequestParam(defaultValue = "sessao-unica-123") String conversationId
    ) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                
        return chatClient.prompt()
                .system(system -> system.param("agora", timestamp))
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
    
    @GetMapping("/history")
    public List<ChatMessageDto> getHistory(
            @RequestParam(defaultValue = "sessao-unica-123") final String conversationId
    ) {
        return chatMemory.get(conversationId).stream()
                .map(ChatMessageDto::fromMessage)
                .collect(Collectors.toList());
    }
    
    
}
