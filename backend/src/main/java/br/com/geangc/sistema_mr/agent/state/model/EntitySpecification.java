package br.com.geangc.sistema_mr.agent.state.model;

import br.com.geangc.sistema_mr.agent.state.DynamicPayload;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EntitySpecification(
        UUID entityId,
        long version,
        Map<String, Object> payload,
        Instant updatedAt,
        StateProvenance provenance
) {
    public EntitySpecification {
        if (entityId == null || version < 0 || updatedAt == null) {
            throw new IllegalArgumentException("A especificação exige entidade, versão e data");
        }
        payload = DynamicPayload.immutableCopy(payload);
        provenance = provenance == null ? StateProvenance.system() : provenance;
    }
}
