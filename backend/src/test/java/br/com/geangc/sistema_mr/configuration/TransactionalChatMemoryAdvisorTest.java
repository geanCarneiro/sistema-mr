package br.com.geangc.sistema_mr.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

class TransactionalChatMemoryAdvisorTest {

    private static final String TIMESTAMP = "2026-08-29T01:00:00Z";

    @Test
    void canonicalUserMessageStoresOriginalTextWithoutRawContent() {
        UserMessage stored = TransactionalChatMemoryAdvisor.canonicalUserMessage("pergunta original", TIMESTAMP);

        assertEquals("pergunta original", stored.getText());
        assertEquals(TIMESTAMP, stored.getMetadata().get("timestamp"));
        assertFalse(stored.getMetadata().containsKey("rawContent"));
    }

    @Test
    void formatsCanonicalHistoryOnlyForTheModelRequest() {
        UserMessage stored = UserMessage.builder()
                .text("pergunta original")
                .metadata(Map.of("timestamp", TIMESTAMP))
                .build();

        var modelMessage = TransactionalChatMemoryAdvisor.messageForModel(stored);

        assertEquals("pergunta original", stored.getText());
        assertEquals("[2026-08-29T01:00:00Z] pergunta original", modelMessage.getText());
    }

    @Test
    void formatsAssistantHistoryOnlyForTheModelRequest() {
        AssistantMessage stored = AssistantMessage.builder()
                .content("resposta original")
                .properties(Map.of("timestamp", TIMESTAMP))
                .build();

        var modelMessage = TransactionalChatMemoryAdvisor.messageForModel(stored);

        assertEquals("resposta original", stored.getText());
        assertEquals("[2026-08-29T01:00:00Z] resposta original", modelMessage.getText());
    }

    @Test
    void avoidsDuplicatingTimestampForLegacyMessages() {
        UserMessage legacy = UserMessage.builder()
                .text("[2026-08-29T01:00:00Z] pergunta antiga")
                .metadata(Map.of(
                        "timestamp", TIMESTAMP,
                        "rawContent", "pergunta antiga"
                ))
                .build();

        var modelMessage = TransactionalChatMemoryAdvisor.messageForModel(legacy);

        assertEquals("[2026-08-29T01:00:00Z] pergunta antiga", modelMessage.getText());
    }
}
