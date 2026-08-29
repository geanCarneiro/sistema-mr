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
        DocumentStatus status,
        String errorMessage,
        int contextTokenCount,
        String embeddingModel,
        Instant createdAt,
        Instant updatedAt
) {}
