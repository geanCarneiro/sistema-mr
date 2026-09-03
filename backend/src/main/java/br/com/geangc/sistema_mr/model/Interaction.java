package br.com.geangc.sistema_mr.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Interaction(
        UUID id,
        UUID userMessageId,
        UUID assistantMessageId,
        String prompt,
        String response,
        String conversationId,
        String ownerSubject,
        Instant createdAt,
        Instant completedAt,
        List<InteractionSource> sources
) {

    public Interaction {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
