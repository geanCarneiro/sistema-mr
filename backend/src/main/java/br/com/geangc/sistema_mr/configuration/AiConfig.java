/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.neo4j.driver.Driver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.neo4j.Neo4jChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.neo4j.Neo4jChatMemoryRepositoryConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;

/**
 *
 * @author gean.carneiro
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository(Driver driver) {
        Neo4jChatMemoryRepositoryConfig config = Neo4jChatMemoryRepositoryConfig.builder()
                .withDriver(driver)
                .build();

        Neo4jChatMemoryRepository targetRepository = new Neo4jChatMemoryRepository(config);
        
        // Retorna o Decorator que limpa os metadados do Gemini antes de salvar no Neo4j
        return new SanitizedNeo4jChatMemoryRepository(targetRepository);
    }
    
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ToolCallingManager toolCallingManager() {
        return DefaultToolCallingManager.builder().build();
    }

    @Bean("chatSystemInstruction")
    public String chatSystemInstruction(
            @Value("classpath:prompts/system-instruction.md") Resource systemInstruction
    ) throws IOException {
        String content = systemInstruction.getContentAsString(StandardCharsets.UTF_8).strip();
        if (content.isBlank()) {
            throw new IllegalStateException("A instrução de sistema não pode estar vazia");
        }
        return content;
    }

    @Bean("documentVisionChatClient")
    public ChatClient documentVisionChatClient(
            ChatModel chatModel,
            DocumentVisionProperties properties,
            @Value("classpath:prompts/document-vision-system-instruction.md") Resource systemInstruction
    ) throws IOException {
        final String systemPrompt = systemInstruction.getContentAsString(StandardCharsets.UTF_8).strip();
        if (systemPrompt.isBlank()) {
            throw new IllegalStateException("A instrução de sistema do processador visual não pode estar vazia");
        }

        GoogleGenAiThinkingLevel thinkingLevel;
        try {
            thinkingLevel = GoogleGenAiThinkingLevel.valueOf(properties.thinkingLevel().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Nível de raciocínio inválido para o processador visual: " + properties.thinkingLevel(),
                    exception
            );
        }

        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model(properties.model())
                        .temperature(properties.temperature())
                        .thinkingLevel(thinkingLevel)
                        .includeThoughts(false)
                        .googleSearchRetrieval(false)
                        .maxOutputTokens(properties.maxOutputTokens())
                        .responseMimeType("application/json")
                        .responseSchema(DocumentVisionResponse.JSON_SCHEMA))
                .build();
    }
    
    
}
