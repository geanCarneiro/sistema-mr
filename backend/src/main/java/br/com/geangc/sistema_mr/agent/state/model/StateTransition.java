package br.com.geangc.sistema_mr.agent.state.model;

import br.com.geangc.sistema_mr.agent.state.DynamicPayload;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StateTransition(
        UUID id,
        UUID entityId,
        long fromVersion,
        long toVersion,
        Map<String, Object> previousPayload,
        Map<String, Object> payload,
        String origin,
        double confidence,
        String reason,
        Instant occurredAt,
        String idempotencyKey,
        String fingerprint,
        StateProvenance provenance
) {
    public StateTransition {
        if (id == null || entityId == null || fromVersion < 0 || toVersion != fromVersion + 1
                || origin == null || origin.isBlank() || reason == null || reason.isBlank()
                || occurredAt == null || idempotencyKey == null || idempotencyKey.isBlank()
                || fingerprint == null || fingerprint.isBlank()
                || confidence < 0 || confidence > 1 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("A transição persistida possui envelope inválido");
        }
        previousPayload = DynamicPayload.immutableCopy(previousPayload);
        payload = DynamicPayload.immutableCopy(payload);
        provenance = provenance == null ? StateProvenance.system() : provenance;
    }
}
