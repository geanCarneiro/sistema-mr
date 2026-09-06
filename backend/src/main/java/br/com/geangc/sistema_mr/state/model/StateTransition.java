package br.com.geangc.sistema_mr.state.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.LinkedHashMap;

public record StateTransition(
        UUID id,
        UUID entityId,
        String ownerSubject,
        String contextId,
        int fromVersion,
        int toVersion,
        String operation,
        Map<String, Object> beforePayload,
        Map<String, Object> afterPayload,
        Map<String, Object> changes,
        StateChangeOrigin origin,
        double confidence,
        String reason,
        List<Provenance> provenance,
        String idempotencyKey,
        Instant occurredAt
) {
    public StateTransition {
        beforePayload = beforePayload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(beforePayload));
        afterPayload = afterPayload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(afterPayload));
        changes = changes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(changes));
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }
}
