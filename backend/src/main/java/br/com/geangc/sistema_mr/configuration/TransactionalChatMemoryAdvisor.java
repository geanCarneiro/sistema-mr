/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;

/**
 *
 * @author gean.carneiro
 */
public class TransactionalChatMemoryAdvisor implements CallAdvisor  {

    private final ChatMemory chatMemory;

    public TransactionalChatMemoryAdvisor(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        String conversationId = (String) chatClientRequest.context()
                .getOrDefault(ChatMemory.CONVERSATION_ID, "default");

        var time = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        // 1. (Opcional) Carrega o histórico anterior para enviar ao modelo
        // List<Message> history = chatMemory.get(conversationId, 10);

        // 2. Executa a chamada do Gemini/Tools
        // Se der erro AQUI (ex: API down, script falhou), o código dispara exceção e NÃO salva nada!
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        
        // 3. SÓ CHEGA AQUI SE DEU TUDO CERTO! 
        // Agora sim salvamos a mensagem do usuário e a resposta no Neo4j com segurança.
        chatMemory.add(conversationId, chatClientRequest.prompt()
                                        .getUserMessage()
                                        .mutate()
                                        .metadata(Map.of("timestamp", time))
                                        .build()
                        );
        
        var resp = response.chatResponse();
        
        if (resp != null && resp.getResult() != null) {
            
            final HashMap<String, Object> assistantMetadata = new HashMap(resp.getResult().getOutput().getMetadata());
            
            assistantMetadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            chatMemory.add(conversationId, AssistantMessage.builder()
                                .content(resp.getResult().getOutput().getText())
                                .properties(assistantMetadata)
                                .build());
        }

        return response;
    }

    @Override
    public String getName() {
        return "TransactionalChatMemoryAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

}
