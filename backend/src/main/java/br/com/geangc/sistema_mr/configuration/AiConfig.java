/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.configuration;

import br.com.geangc.sistema_mr.tool_calling.PythonToolConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.neo4j.Neo4jChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author gean.carneiro
 */
@Configuration
public class AiConfig {
        
    @Bean
    public ChatMemory chatMemory(Neo4jChatMemoryRepository neo4jChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(neo4jChatMemoryRepository)
                .maxMessages(20)
                .build();
    }
    
    @Bean
    public ChatClient chatClient(
            ChatModel chatModel, 
            ChatMemory chatMemory,
            PythonToolConfig pythonToolConfig
    ) {
        
        final String systemPrompt = 
                """
                    1. Não invente data ou hora. Use estritamente o carimbo de referência fornecido a seguir: {agora}
                    2. Nunca faça cálculos matemáticos ou algoritmos determinísticos diretamente na resposta. Sempre use a Tool de execução de código Python para isso.
                       2.1. Se precisar realizar múltiplos cálculos, agrupe todos em um único script Python para resolver em uma só chamada de Tool.
                """;
        
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(pythonToolConfig)
                .build();
    }
    
    
}
