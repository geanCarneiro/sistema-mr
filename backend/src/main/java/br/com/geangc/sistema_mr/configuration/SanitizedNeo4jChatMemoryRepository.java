/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.neo4j.Neo4jChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

/**
 *
 * @author geanCarneiro
 */
public class SanitizedNeo4jChatMemoryRepository implements ChatMemoryRepository {

    private final Neo4jChatMemoryRepository delegate;

    public SanitizedNeo4jChatMemoryRepository(Neo4jChatMemoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return delegate.findByConversationId(conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // Sanitiza os metadados das mensagens antes de repassar ao repositório Neo4j
        for (Message message : messages) {
            if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
                Map<String, Object> sanitizedMetadata = new HashMap<>();
                message.getMetadata().forEach((key, value) -> {
                    // Converte Enums/Objetos complexos (como o FinishReason do Gemini) para String
                    if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                        sanitizedMetadata.put(key, value.toString());
                    } else {
                        sanitizedMetadata.put(key, value);
                    }
                });
                message.getMetadata().clear();
                message.getMetadata().putAll(sanitizedMetadata);
            }
        }
        delegate.saveAll(conversationId, messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        delegate.deleteByConversationId(conversationId);
    }

    @Override
    public List<String> findConversationIds() {
        return delegate.findConversationIds();
    }
}
