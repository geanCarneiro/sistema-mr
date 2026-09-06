package br.com.geangc.sistema_mr.agent.state.model;

import br.com.geangc.sistema_mr.agent.state.DynamicPayload;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record StateObservation(
        UUID id,
        UUID entityId,
        Map<String, Object> payload,
        String origin,
        double confidence,
        Instant observedAt,
        StateProvenance provenance
) {
    public StateObservation {
        if (id == null || entityId == null || origin == null || origin.isBlank() || observedAt == null
                || confidence < 0 || confidence > 1 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("A observação exige id, entidade, origem, data e confiança válida");
        }
        payload = DynamicPayload.immutableCopy(payload);
        provenance = provenance == null ? StateProvenance.system() : provenance;
    }
}
