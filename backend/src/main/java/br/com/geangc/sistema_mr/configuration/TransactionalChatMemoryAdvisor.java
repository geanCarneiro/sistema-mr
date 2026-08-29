/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.configuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 *
 * @author gean.carneiro
 */
public class TransactionalChatMemoryAdvisor implements CallAdvisor  {

    private final Logger logger = LoggerFactory.getLogger(TransactionalChatMemoryAdvisor.class);
    
    private final ChatMemory chatMemory;

    public TransactionalChatMemoryAdvisor(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        String conversationId = (String) chatClientRequest.context()
                .getOrDefault(ChatMemory.CONVERSATION_ID, "default");

        String userTime = Instant.now().toString();

        // Pega a mensagem de usuário caso ela exista no prompt original
        UserMessage currentUserMessage = chatClientRequest.prompt().getUserMessage();
        UserMessage updatedUserMessage = null;

        if (currentUserMessage != null && currentUserMessage.getText() != null) {
            String rawPrompt = currentUserMessage.getText();
            String formattedUserPrompt = String.format("[%s] %s", userTime, rawPrompt);

            Map<String, Object> userMetadata = Map.of(
                    "timestamp", userTime,
                    "rawContent", rawPrompt
            );
            updatedUserMessage = UserMessage.builder()
                    .text(formattedUserPrompt)
                    .metadata(userMetadata)
                    .build();
        }

        // SE A REQUISIÇÃO JÁ É UM RETORNO DE TOOL (ou contém chamadas de Tool pendentes),
        // NÃO PODEMOS REORGANIZAR OU REINJETAR O HISTÓRICO PARA NÃO QUEBRAR O GEMINI!
        boolean isToolTurn = chatClientRequest.prompt().getInstructions().stream()
                .anyMatch(msg -> msg instanceof ToolResponseMessage || 
                         (msg instanceof AssistantMessage am && am.hasToolCalls()));

        ChatClientRequest finalRequest = chatClientRequest;

        if (!isToolTurn) {
            List<Message> history = chatMemory.get(conversationId);

            List<Message> fullInstructions = new ArrayList<>();

            // 1. Mensagens de Sistema (SystemMessage)
            chatClientRequest.prompt().getInstructions().stream()
                    .filter(msg -> !(msg instanceof UserMessage))
                    .forEach(fullInstructions::add);

            // 2. Histórico da conversa recuperado do banco
            fullInstructions.addAll(history);

            // 3. Mensagem do usuário atual
            if (updatedUserMessage != null) {
                fullInstructions.add(updatedUserMessage);
            }

            Prompt newPrompt = new Prompt(fullInstructions, chatClientRequest.prompt().getOptions());
            finalRequest = chatClientRequest.mutate().prompt(newPrompt).build();
        }

        // Executa a chamada
        ChatClientResponse response = callAdvisorChain.nextCall(finalRequest);

        var resp = response.chatResponse();
        if (resp != null && resp.getResult() != null) {

            // Não grava no histórico se a resposta intermediária do modelo for um disparo de Tool
            if (resp.getResult().getOutput().hasToolCalls()) {
                return response;
            }

            // Grava as mensagens no histórico somente na resposta final do texto
            if (updatedUserMessage != null) {
                chatMemory.add(conversationId, updatedUserMessage);
            }

            String assistantTime = Instant.now().toString();
            Map<String, Object> assistantMetadata = new HashMap<>(resp.getResult().getOutput().getMetadata());

            String rawResponse = resp.getResult().getOutput().getText();
            if (rawResponse != null && !rawResponse.isBlank()) {
                assistantMetadata.put("timestamp", assistantTime);
                assistantMetadata.put("rawContent", rawResponse);

                AssistantMessage assistantMessage = AssistantMessage.builder()
                        .content(String.format("[%s] %s", assistantTime, rawResponse))
                        .properties(assistantMetadata)
                        .build();

                chatMemory.add(conversationId, assistantMessage);
            
                // --- O SEGREDO ESTÁ AQUI: VOCÊ PRECISA DEVOLVER A MENSAGEM ALTERADA ---

                // 1. Recriamos a geração de resposta (Generation) com a nossa nova mensagem
                Generation novaGeneration = new Generation(assistantMessage, resp.getResult().getMetadata());

                // 2. Recriamos o ChatResponse substituindo o resultado antigo pelo novo
                ChatResponse novoChatResponse = new ChatResponse(List.of(novaGeneration), resp.getMetadata());

                // 3. Mutamos a resposta final do ChatClientResponse para usar o nosso ChatResponse enriquecido
                return ChatClientResponse.builder()
                        .chatResponse(novoChatResponse)
                        .build();
            
            }
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
