package br.com.geangc.sistema_mr.agent.state.model;

import br.com.geangc.sistema_mr.agent.state.DynamicPayload;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StateTransitionCommand(
        UUID entityId,
        String ownerSubject,
        String contextId,
        long expectedVersion,
        Map<String, Object> payload,
        String origin,
        double confidence,
        String reason,
        String idempotencyKey,
        Instant occurredAt,
        StateProvenance provenance
) {
    public StateTransitionCommand {
        if (entityId == null || ownerSubject == null || ownerSubject.isBlank()
                || contextId == null || contextId.isBlank() || expectedVersion < 0
                || origin == null || origin.isBlank() || reason == null || reason.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("A transição exige entidade, escopo, versão, origem, motivo e idempotência");
        }
        if (confidence < 0 || confidence > 1 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("A confiança deve estar entre 0 e 1");
        }
        payload = DynamicPayload.immutableCopy(payload);
        provenance = provenance == null ? StateProvenance.system() : provenance;
    }
}
