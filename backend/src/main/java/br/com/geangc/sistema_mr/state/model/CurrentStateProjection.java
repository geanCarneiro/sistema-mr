package br.com.geangc.sistema_mr.state.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.LinkedHashMap;

public record CurrentStateProjection(
        UUID entityId,
        String type,
        int version,
        Map<String, Object> payload,
        List<Provenance> provenance,
        double confidence,
        Instant updatedAt
) {
    public CurrentStateProjection {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }
}
