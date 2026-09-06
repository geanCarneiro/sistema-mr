package br.com.geangc.sistema_mr.state.model;

import java.time.Instant;
import java.util.List;

public record StateChangeMetadata(
        StateChangeOrigin origin,
        double confidence,
        String reason,
        Instant occurredAt,
        String idempotencyKey,
        List<Provenance> provenance
) {
    public StateChangeMetadata {
        if (origin == null) {
            throw new IllegalArgumentException("A origem da alteração é obrigatória");
        }
        if (confidence < 0.0 || confidence > 1.0 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("A confiança deve estar entre 0 e 1");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("O motivo da alteração é obrigatório");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("O timestamp da alteração é obrigatório");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("A chave de idempotência é obrigatória");
        }
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }

    public static StateChangeMetadata of(
            StateChangeOrigin origin,
            double confidence,
            String reason,
            String idempotencyKey
    ) {
        return new StateChangeMetadata(origin, confidence, reason, Instant.now(), idempotencyKey, List.of());
    }
}
