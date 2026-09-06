package br.com.geangc.sistema_mr.memory.model;

import br.com.geangc.sistema_mr.state.model.Provenance;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SemanticMemory(
        UUID id,
        String ownerSubject,
        String contextId,
        String subjectId,
        String key,
        String value,
        MemoryOrigin origin,
        double confidence,
        List<Provenance> provenance,
        Instant validFrom,
        Instant validUntil,
        MemoryRetentionPolicy retentionPolicy,
        MemoryStatus status,
        int version,
        UUID supersedesMemoryId,
        UUID supersededByMemoryId,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
    public SemanticMemory {
        if (id == null || ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException("A memória precisa de identificador e proprietário");
        }
        if (contextId == null || contextId.isBlank()) {
            throw new IllegalArgumentException("O contexto da memória é obrigatório");
        }
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("A memória precisa de chave e valor");
        }
        if (origin == null || retentionPolicy == null || status == null) {
            throw new IllegalArgumentException("A memória precisa de origem, retenção e estado");
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("A confiança da memória deve estar entre 0 e 1");
        }
        if (retentionPolicy == MemoryRetentionPolicy.UNTIL_VALID_UNTIL && validUntil == null) {
            throw new IllegalArgumentException("A retenção UNTIL_VALID_UNTIL exige validade");
        }
        if (version < 1) {
            throw new IllegalArgumentException("A versão da memória deve ser positiva");
        }
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }
}
