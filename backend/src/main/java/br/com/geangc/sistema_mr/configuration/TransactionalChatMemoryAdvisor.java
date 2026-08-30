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
import java.util.Optional;
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

    public static final String ORIGINAL_USER_PROMPT = "original-user-prompt";

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
        UserMessage modelUserMessage = null;
        UserMessage persistedUserMessage = null;

        if (currentUserMessage != null && currentUserMessage.getText() != null) {
            String modelPrompt = currentUserMessage.getText();
            String originalPrompt = (String) chatClientRequest.context()
                    .getOrDefault(ORIGINAL_USER_PROMPT, modelPrompt);

            persistedUserMessage = canonicalUserMessage(originalPrompt, userTime);
            modelUserMessage = UserMessage.builder()
                    .text(formatForModel(modelPrompt, userTime))
                    .metadata(Map.of("timestamp", userTime))
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
            history.stream()
                    .map(TransactionalChatMemoryAdvisor::messageForModel)
                    .forEach(fullInstructions::add);

            // 3. Mensagem do usuário atual
            if (modelUserMessage != null) {
                fullInstructions.add(modelUserMessage);
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
            if (persistedUserMessage != null) {
                chatMemory.add(conversationId, persistedUserMessage);
            }

            String assistantTime = Instant.now().toString();
            Map<String, Object> assistantMetadata = new HashMap<>(resp.getResult().getOutput().getMetadata());

            String rawResponse = resp.getResult().getOutput().getText();
            if (rawResponse != null && !rawResponse.isBlank()) {
                assistantMetadata.put("timestamp", assistantTime);
                assistantMetadata.remove("rawContent");

                AssistantMessage assistantMessage = AssistantMessage.builder()
                        .content(rawResponse)
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

    static UserMessage canonicalUserMessage(String prompt, String timestamp) {
        return UserMessage.builder()
                .text(prompt)
                .metadata(Map.of("timestamp", timestamp))
                .build();
    }

    static Message messageForModel(Message message) {
        String timestamp = Optional.ofNullable(message.getMetadata().get("timestamp"))
                .map(Object::toString)
                .orElse(null);
        if (timestamp == null || timestamp.isBlank() || message.getText() == null) {
            return message;
        }

        String canonicalText = Optional.ofNullable(message.getMetadata().get("rawContent"))
                .map(Object::toString)
                .orElseGet(message::getText);
        String formattedText = formatForModel(canonicalText, timestamp);

        if (message instanceof UserMessage) {
            return UserMessage.builder()
                    .text(formattedText)
                    .metadata(new HashMap<>(message.getMetadata()))
                    .build();
        }
        if (message instanceof AssistantMessage assistantMessage && !assistantMessage.hasToolCalls()) {
            return AssistantMessage.builder()
                    .content(formattedText)
                    .properties(new HashMap<>(message.getMetadata()))
                    .build();
        }
        return message;
    }

    private static String formatForModel(String content, String timestamp) {
        return String.format("[%s] %s", timestamp, content);
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
