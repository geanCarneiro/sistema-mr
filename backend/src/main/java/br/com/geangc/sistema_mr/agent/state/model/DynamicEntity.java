package br.com.geangc.sistema_mr.agent.state.model;

import java.time.Instant;
import java.util.UUID;

public record DynamicEntity(
        UUID id,
        String ownerSubject,
        String contextId,
        String type,
        Instant createdAt
) {
    public DynamicEntity {
        if (id == null || ownerSubject == null || ownerSubject.isBlank()
                || contextId == null || contextId.isBlank()
                || type == null || type.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("A entidade dinâmica exige id, usuário, contexto, tipo e data");
        }
    }
}
