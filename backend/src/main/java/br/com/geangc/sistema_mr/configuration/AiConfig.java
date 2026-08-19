/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.configuration;

import br.com.geangc.sistema_mr.tool_calling.PythonToolConfig;
import org.neo4j.driver.Driver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.neo4j.Neo4jChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.neo4j.Neo4jChatMemoryRepositoryConfig;
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
    public ChatMemoryRepository chatMemoryRepository(Driver driver) {
        Neo4jChatMemoryRepositoryConfig config = Neo4jChatMemoryRepositoryConfig.builder()
                .withDriver(driver)
                .build();

        Neo4jChatMemoryRepository targetRepository = new Neo4jChatMemoryRepository(config);
        
        // Retorna o Decorator que limpa os metadados do Gemini antes de salvar no Neo4j
        return new SanitizedNeo4jChatMemoryRepository(targetRepository);
    }
    
    private ChatMemory createChatMemory(ChatMemoryRepository repository) {
                
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }
    
    
    @Bean
    public ChatClient chatClient(
            ChatModel chatModel, 
            ChatMemoryRepository repository,
            PythonToolConfig pythonToolConfig
    ) {
        
        ChatMemory chatMemory = createChatMemory(repository);
        
        final String systemPrompt = 
                """
                    1. Não invente data ou hora. Use estritamente o carimbo de referência fornecido a seguir: {agora}
                    2. Nunca faça cálculos matemáticos ou algoritmos determinísticos diretamente na resposta. Sempre use a Tool de execução de código Python para isso.
                       2.1. Se precisar realizar múltiplos cálculos, agrupe todos em um único script Python para resolver em uma só chamada de Tool.
                       2.2. Conversão de formato de valores não entram nessa regra quando não envolver alteração factual do dado, apenas formatação.
                """;
        
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(new TransactionalChatMemoryAdvisor(chatMemory))
                .defaultTools(pythonToolConfig)
                .build();
    }
    
    
}
