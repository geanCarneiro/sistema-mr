package br.com.geangc.sistema_mr.model;

import java.time.Instant;
import java.util.UUID;

public record ChatFile(
        UUID id,
        String conversationId,
        String ownerSubject,
        String originalName,
        String mimeType,
        long size,
        String sha256,
        String originalStorageKey,
        String contextStorageKey,
        String fullContextStorageKey,
        String mappingStorageKey,
        DocumentStatus status,
        String errorMessage,
        int contextTokenCount,
        String embeddingModel,
        Instant createdAt,
        Instant updatedAt,
        DocumentSensitivity sensitivity
) {
    public ChatFile(
            UUID id,
            String conversationId,
            String ownerSubject,
            String originalName,
            String mimeType,
            long size,
            String sha256,
            String originalStorageKey,
            String contextStorageKey,
            DocumentStatus status,
            String errorMessage,
            int contextTokenCount,
            String embeddingModel,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, conversationId, ownerSubject, originalName, mimeType, size, sha256,
                originalStorageKey, contextStorageKey, null, null, status, errorMessage,
                contextTokenCount, embeddingModel, createdAt, updatedAt, DocumentSensitivity.NORMAL);
    }

    public ChatFile(
            UUID id,
            String conversationId,
            String ownerSubject,
            String originalName,
            String mimeType,
            long size,
            String sha256,
            String originalStorageKey,
            String contextStorageKey,
            DocumentStatus status,
            String errorMessage,
            int contextTokenCount,
            String embeddingModel,
            Instant createdAt,
            Instant updatedAt,
            DocumentSensitivity sensitivity
    ) {
        this(id, conversationId, ownerSubject, originalName, mimeType, size, sha256,
                originalStorageKey, contextStorageKey, null, null, status, errorMessage,
                contextTokenCount, embeddingModel, createdAt, updatedAt, sensitivity);
    }
}
