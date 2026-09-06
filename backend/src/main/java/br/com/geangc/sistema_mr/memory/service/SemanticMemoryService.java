package br.com.geangc.sistema_mr.memory.service;

import br.com.geangc.sistema_mr.memory.model.MemoryOrigin;
import br.com.geangc.sistema_mr.memory.model.MemoryRetentionPolicy;
import br.com.geangc.sistema_mr.memory.model.MemoryStatus;
import br.com.geangc.sistema_mr.memory.model.SemanticMemory;
import br.com.geangc.sistema_mr.memory.repository.SemanticMemoryRepository;
import br.com.geangc.sistema_mr.state.model.Provenance;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SemanticMemoryService {

    private final SemanticMemoryRepository repository;

    public SemanticMemoryService(SemanticMemoryRepository repository) {
        this.repository = repository;
    }

    public SemanticMemory remember(
            String ownerSubject, String contextId, String subjectId, String key, String value,
            String origin, double confidence, String validUntil, String retentionPolicy,
            Provenance provenance
    ) {
        Instant now = Instant.now();
        MemoryRetentionPolicy policy = parseRetention(retentionPolicy);
        Instant expiration = parseInstant(validUntil, "validUntil");
        validateExpiration(policy, expiration);
        return repository.create(new SemanticMemory(
                UUID.randomUUID(), required(ownerSubject, "ownerSubject"), required(contextId, "contextId"),
                blankToNull(subjectId), required(key, "key"), required(value, "value"),
                parseOrigin(origin), confidence, List.of(provenance), now, expiration, policy,
                MemoryStatus.ACTIVE, 1, null, null, now, now, null
        ));
    }

    public List<SemanticMemory> findRelevant(String ownerSubject, String contextId, String query, int limit) {
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("O limite de memórias deve estar entre 1 e 20");
        }
        return repository.findRelevant(
                required(ownerSubject, "ownerSubject"), required(contextId, "contextId"), terms(query), limit, Instant.now());
    }

    public SemanticMemory update(
            UUID memoryId, String ownerSubject, String contextId, int expectedVersion,
            String key, String value, String origin, double confidence, String validUntil,
            String retentionPolicy, Provenance provenance
    ) {
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("A versão esperada deve ser positiva");
        }
        MemoryRetentionPolicy policy = parseRetention(retentionPolicy);
        Instant expiration = parseInstant(validUntil, "validUntil");
        validateExpiration(policy, expiration);
        Instant now = Instant.now();
        SemanticMemory replacement = new SemanticMemory(
                UUID.randomUUID(), required(ownerSubject, "ownerSubject"), required(contextId, "contextId"),
                null, required(key, "key"), required(value, "value"), parseOrigin(origin), confidence,
                List.of(provenance), now, expiration, policy, MemoryStatus.ACTIVE, 1,
                memoryId, null, now, now, null
        );
        return repository.replace(memoryId, ownerSubject, contextId, expectedVersion, replacement)
                .orElseThrow(() -> new IllegalStateException(
                        "A memória não existe, não pertence ao escopo ou foi alterada por outra execução"));
    }

    public void delete(UUID memoryId, String ownerSubject, String contextId, int expectedVersion) {
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("A versão esperada deve ser positiva");
        }
        if (!repository.delete(memoryId, required(ownerSubject, "ownerSubject"),
                required(contextId, "contextId"), expectedVersion, Instant.now())) {
            throw new IllegalStateException(
                    "A memória não existe, não pertence ao escopo ou foi alterada por outra execução");
        }
    }

    public static MemoryOrigin parseOrigin(String value) {
        try {
            return MemoryOrigin.valueOf(required(value, "origin").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("A origem deve ser USER_DECLARED ou MODEL_INFERRED", exception);
        }
    }

    public static MemoryRetentionPolicy parseRetention(String value) {
        try {
            return MemoryRetentionPolicy.valueOf(required(value, "retentionPolicy").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "A política deve ser UNTIL_REVOKED ou UNTIL_VALID_UNTIL", exception);
        }
    }

    private static void validateExpiration(MemoryRetentionPolicy policy, Instant expiration) {
        if (policy == MemoryRetentionPolicy.UNTIL_VALID_UNTIL && expiration == null) {
            throw new IllegalArgumentException("A retenção UNTIL_VALID_UNTIL exige validUntil");
        }
    }

    private static List<String> terms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() >= 3).distinct().limit(8).toList();
    }

    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("O campo " + field + " deve ser um instante ISO-8601", exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("O campo " + field + " é obrigatório");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
