package br.com.geangc.sistema_mr.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

public final class ChatMemoryMessageFormatter {

    private ChatMemoryMessageFormatter() {}

    public static UserMessage canonicalUserMessage(String prompt, String timestamp) {
        return UserMessage.builder()
                .text(prompt)
                .metadata(Map.of("timestamp", timestamp))
                .build();
    }

    public static Message messageForModel(Message message) {
        String timestamp = Optional.ofNullable(message.getMetadata().get("timestamp"))
                .map(Object::toString)
                .orElse(null);
        if (timestamp == null || timestamp.isBlank() || message.getText() == null) {
            return message;
        }

        String canonicalText = Optional.ofNullable(message.getMetadata().get("rawContent"))
                .map(Object::toString)
                .orElseGet(message::getText);
        String formattedText = "[" + timestamp + "] " + canonicalText;
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
}
