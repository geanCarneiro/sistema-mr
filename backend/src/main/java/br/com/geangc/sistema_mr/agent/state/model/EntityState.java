package br.com.geangc.sistema_mr.agent.state.model;

import br.com.geangc.sistema_mr.agent.state.DynamicPayload;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EntityState(
        UUID entityId,
        long version,
        Map<String, Object> payload,
        String origin,
        double confidence,
        String reason,
        Instant updatedAt,
        StateProvenance provenance,
        UUID transitionId
) {
    public EntityState {
        payload = DynamicPayload.immutableCopy(payload);
        provenance = provenance == null ? StateProvenance.system() : provenance;
    }
}
