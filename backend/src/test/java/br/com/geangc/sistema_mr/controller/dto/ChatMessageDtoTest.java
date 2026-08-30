package br.com.geangc.sistema_mr.controller.dto;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageDtoTest {

    @Test
    void mapsCanonicalMessageTextAndTimestampMetadata() {
        UserMessage message = UserMessage.builder()
                .text("conteúdo")
                .metadata(Map.of("timestamp", "2026-08-28T10:00:00Z"))
                .build();

        ChatMessageDto dto = ChatMessageDto.fromMessage(message);

        assertEquals("USER", dto.messageType());
        assertEquals("2026-08-28T10:00:00Z", dto.timestamp());
        assertEquals("conteúdo", dto.content());
    }

    @Test
    void keepsCompatibilityWithLegacyRawContentMessages() {
        UserMessage message = UserMessage.builder()
                .text("[2026-08-28T10:00:00Z] conteúdo")
                .metadata(Map.of(
                        "timestamp", "2026-08-28T10:00:00Z",
                        "rawContent", "conteúdo"
                ))
                .build();

        ChatMessageDto dto = ChatMessageDto.fromMessage(message);

        assertEquals("USER", dto.messageType());
        assertEquals("2026-08-28T10:00:00Z", dto.timestamp());
        assertEquals("conteúdo", dto.content());
    }
}
