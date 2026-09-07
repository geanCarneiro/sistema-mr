/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package br.com.geangc.sistema_mr.controller.dto;

import br.com.geangc.sistema_mr.model.Interaction;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.messages.Message;

/**
 *
 * @author gean.carneiro
 */
public record ChatMessageDto(
        String messageId,
        String interactionId,
        String messageType,
        String timestamp,
        String content,
        List<GroundingFileDto> groundingFiles,
        String messageKind,
        String privacyFileId
) {

    public ChatMessageDto {
        groundingFiles = groundingFiles == null ? List.of() : List.copyOf(groundingFiles);
    }

    public ChatMessageDto(
            String messageId,
            String interactionId,
            String messageType,
            String timestamp,
            String content,
            List<GroundingFileDto> groundingFiles
    ) {
        this(messageId, interactionId, messageType, timestamp, content, groundingFiles, "NORMAL", null);
    }

    public static ChatMessageDto fromMessage(Message message) {
        String messageId = Optional.ofNullable(message.getMetadata().get("messageId"))
                .map(Object::toString)
                .orElse(null);
        String interactionId = Optional.ofNullable(message.getMetadata().get("interactionId"))
                .map(Object::toString)
                .orElse(null);
        return new ChatMessageDto(
                messageId,
                interactionId,
                message.getMessageType().toString(),
                Optional.ofNullable(message.getMetadata().get("timestamp"))
                        .map(Object::toString)
                        .orElse(null),
                Optional.ofNullable(message.getMetadata().get("rawContent"))
                        .map(Object::toString)
                        .orElseGet(message::getText),
                List.of(),
                Optional.ofNullable(message.getMetadata().get("messageKind"))
                        .map(Object::toString)
                        .orElse(null),
                Optional.ofNullable(message.getMetadata().get("privacyFileId"))
                        .map(Object::toString)
                        .orElse(null)
        );
    }

    public static ChatMessageDto userFrom(Interaction interaction) {
        return new ChatMessageDto(
                interaction.userMessageId().toString(),
                interaction.id().toString(),
                "USER",
                interaction.createdAt().toString(),
                interaction.prompt(),
                List.of(),
                "NORMAL",
                null
        );
    }

    public static ChatMessageDto assistantFrom(Interaction interaction) {
        return new ChatMessageDto(
                interaction.assistantMessageId().toString(),
                interaction.id().toString(),
                "ASSISTANT",
                interaction.completedAt().toString(),
                interaction.response(),
                interaction.sources().stream().map(GroundingFileDto::from).toList(),
                "NORMAL",
                null
        );
    }
}
