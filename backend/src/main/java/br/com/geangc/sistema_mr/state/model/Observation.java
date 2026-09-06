package br.com.geangc.sistema_mr.state.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.LinkedHashMap;

public record Observation(
        UUID id,
        UUID entityId,
        String ownerSubject,
        String contextId,
        String type,
        Map<String, Object> payload,
        StateChangeOrigin origin,
        double confidence,
        List<Provenance> provenance,
        Instant observedAt
) {
    public Observation {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }
}
