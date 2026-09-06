package br.com.geangc.sistema_mr.state.model;

import java.time.Instant;
import java.util.UUID;

public record DynamicEntity(
        UUID id,
        String ownerSubject,
        String contextId,
        String type,
        Instant createdAt,
        Instant updatedAt
) {}
