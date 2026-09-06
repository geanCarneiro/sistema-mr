package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.configuration.SanitizedNeo4jChatMemoryRepository;
import br.com.geangc.sistema_mr.controller.dto.ChatMessageDto;
import br.com.geangc.sistema_mr.model.Interaction;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryService {

    private final ConversationScopeService conversationScopeService;
    private final SanitizedNeo4jChatMemoryRepository chatMemoryRepository;
    private final InteractionService interactionService;

    public ChatHistoryService(
            ConversationScopeService conversationScopeService,
            SanitizedNeo4jChatMemoryRepository chatMemoryRepository,
            InteractionService interactionService
    ) {
        this.conversationScopeService = conversationScopeService;
        this.chatMemoryRepository = chatMemoryRepository;
        this.interactionService = interactionService;
    }

    public List<ChatMessageDto> find(String ownerSubject) {
        String conversationId = conversationScopeService.resolve(ownerSubject).conversationId();
        List<Interaction> interactions = interactionService.findHistory(conversationId, ownerSubject);
        List<ChatMessageDto> persistedHistory = interactions.stream()
                .flatMap(interaction -> Stream.of(
                        ChatMessageDto.userFrom(interaction),
                        ChatMessageDto.assistantFrom(interaction)
                ))
                .collect(Collectors.toList());

        Set<String> persistedIds = new HashSet<>();
        interactions.forEach(interaction -> {
            persistedIds.add(interaction.id().toString());
            persistedIds.add(interaction.userMessageId().toString());
            persistedIds.add(interaction.assistantMessageId().toString());
        });

        List<ChatMessageDto> legacyHistory = chatMemoryRepository.findByConversationId(conversationId).stream()
                .map(ChatMessageDto::fromMessage)
                .filter(message -> !persistedIds.contains(message.interactionId())
                        && !persistedIds.contains(message.messageId()))
                .toList();

        return Stream.concat(persistedHistory.stream(), legacyHistory.stream())
                .sorted(Comparator.comparing(
                        ChatMessageDto::timestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();
    }
}
