package br.com.geangc.sistema_mr.memory.repository;

import br.com.geangc.sistema_mr.memory.model.MemoryOrigin;
import br.com.geangc.sistema_mr.memory.model.MemoryRetentionPolicy;
import br.com.geangc.sistema_mr.memory.model.MemoryStatus;
import br.com.geangc.sistema_mr.memory.model.SemanticMemory;
import br.com.geangc.sistema_mr.state.model.Provenance;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class SemanticMemoryRepository {

    private final Driver driver;
    private final ObjectMapper objectMapper;

    public SemanticMemoryRepository(Driver driver, ObjectMapper objectMapper) {
        this.driver = driver;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initializeSchema() {
        try (var session = driver.session()) {
            session.run("CREATE CONSTRAINT semantic_memory_id IF NOT EXISTS "
                    + "FOR (n:MemoriaSemantica) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE INDEX semantic_memory_scope IF NOT EXISTS "
                    + "FOR (n:MemoriaSemantica) ON (n.ownerSubject, n.contextId, n.status)").consume();
            session.run("CREATE INDEX semantic_memory_key IF NOT EXISTS "
                    + "FOR (n:MemoriaSemantica) ON (n.ownerSubject, n.key)").consume();
        }
    }

    public SemanticMemory create(SemanticMemory memory) {
        String query = """
                MERGE (context:ContextoChat {id: $contextId})
                ON CREATE SET context.ownerSubject = $ownerSubject,
                              context.createdAt = $createdAt
                WITH context
                WHERE context.ownerSubject = $ownerSubject
                CREATE (memory:MemoriaSemantica {
                    id: $id, ownerSubject: $ownerSubject, contextId: $contextId,
                    subjectId: $subjectId, key: $key, value: $value,
                    origin: $origin, confidence: $confidence,
                    provenanceJson: $provenanceJson, validFrom: $validFrom,
                    validUntil: $validUntil, retentionPolicy: $retentionPolicy,
                    status: $status, version: $version,
                    supersedesMemoryId: $supersedesMemoryId,
                    createdAt: $createdAt, updatedAt: $updatedAt
                })
                CREATE (context)-[:POSSUI_MEMORIA]->(memory)
                RETURN memory
                """;
        try (var session = driver.session()) {
            return session.executeWrite(transaction -> {
                var result = transaction.run(query, parameters(memory));
                if (!result.hasNext()) {
                    throw new IllegalArgumentException("O contexto da memória não pertence ao usuário");
                }
                return mapMemory(result.single());
            });
        }
    }

    public List<SemanticMemory> findRelevant(
            String ownerSubject, String contextId, List<String> terms, int limit, Instant now
    ) {
        String query = """
                MATCH (memory:MemoriaSemantica {
                    ownerSubject: $ownerSubject, contextId: $contextId, status: 'ACTIVE'
                })
                WHERE (memory.validUntil IS NULL OR memory.validUntil > $now)
                  AND (size($terms) = 0 OR any(term IN $terms
                       WHERE toLower(memory.key) CONTAINS term
                          OR toLower(memory.value) CONTAINS term))
                RETURN memory
                ORDER BY memory.confidence DESC, memory.updatedAt DESC
                LIMIT $limit
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "ownerSubject", ownerSubject, "contextId", contextId,
                    "terms", terms, "now", now.toString(), "limit", limit
            )).list(this::mapMemory));
        }
    }

    public Optional<SemanticMemory> replace(
            UUID memoryId, String ownerSubject, String contextId,
            int expectedVersion, SemanticMemory replacement
    ) {
        String query = """
                MATCH (old:MemoriaSemantica {
                    id: $oldId, ownerSubject: $ownerSubject, contextId: $contextId,
                    status: 'ACTIVE', version: $expectedVersion
                })
                SET old.status = 'SUPERSEDED', old.supersededByMemoryId = $newId,
                    old.updatedAt = $updatedAt
                CREATE (new:MemoriaSemantica {
                    id: $newId, ownerSubject: $ownerSubject, contextId: $contextId,
                    subjectId: $subjectId, key: $key, value: $value,
                    origin: $origin, confidence: $confidence,
                    provenanceJson: $provenanceJson, validFrom: $validFrom,
                    validUntil: $validUntil, retentionPolicy: $retentionPolicy,
                    status: 'ACTIVE', version: 1, supersedesMemoryId: $oldId,
                    createdAt: $createdAt, updatedAt: $updatedAt
                })
                CREATE (old)-[:SUBSTITUIDA_POR]->(new)
                RETURN new AS memory
                """;
        Map<String, Object> parameters = parameters(replacement);
        parameters.put("oldId", memoryId.toString());
        parameters.put("expectedVersion", expectedVersion);
        try (var session = driver.session()) {
            return session.executeWrite(transaction -> transaction.run(query, parameters)
                    .stream().findFirst().map(this::mapMemory));
        }
    }

    public boolean delete(UUID memoryId, String ownerSubject, String contextId, int expectedVersion, Instant deletedAt) {
        String query = """
                MATCH (memory:MemoriaSemantica {
                    id: $id, ownerSubject: $ownerSubject, contextId: $contextId,
                    status: 'ACTIVE', version: $expectedVersion
                })
                SET memory.status = 'DELETED', memory.deletedAt = $deletedAt,
                    memory.updatedAt = $deletedAt, memory.version = memory.version + 1
                RETURN memory
                """;
        try (var session = driver.session()) {
            return session.executeWrite(transaction -> transaction.run(query, Map.of(
                    "id", memoryId.toString(), "ownerSubject", ownerSubject,
                    "contextId", contextId, "expectedVersion", expectedVersion,
                    "deletedAt", deletedAt.toString()
            )).hasNext());
        }
    }

    private Map<String, Object> parameters(SemanticMemory memory) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", memory.id().toString());
        parameters.put("ownerSubject", memory.ownerSubject());
        parameters.put("contextId", memory.contextId());
        parameters.put("subjectId", memory.subjectId() == null ? "" : memory.subjectId());
        parameters.put("key", memory.key());
        parameters.put("value", memory.value());
        parameters.put("origin", memory.origin().name());
        parameters.put("confidence", memory.confidence());
        parameters.put("provenanceJson", writeJson(memory.provenance()));
        parameters.put("validFrom", memory.validFrom().toString());
        parameters.put("validUntil", memory.validUntil() == null ? null : memory.validUntil().toString());
        parameters.put("retentionPolicy", memory.retentionPolicy().name());
        parameters.put("status", memory.status().name());
        parameters.put("version", memory.version());
        parameters.put("supersedesMemoryId", memory.supersedesMemoryId() == null
                ? null : memory.supersedesMemoryId().toString());
        parameters.put("createdAt", memory.createdAt().toString());
        parameters.put("updatedAt", memory.updatedAt().toString());
        return parameters;
    }

    private SemanticMemory mapMemory(Record record) {
        Map<String, Object> values = record.get("memory").asMap();
        return new SemanticMemory(
                UUID.fromString(values.get("id").toString()), values.get("ownerSubject").toString(),
                values.get("contextId").toString(), optionalString(values, "subjectId"),
                values.get("key").toString(), values.get("value").toString(),
                MemoryOrigin.valueOf(values.get("origin").toString()),
                ((Number) values.get("confidence")).doubleValue(),
                readProvenance(values.get("provenanceJson").toString()),
                Instant.parse(values.get("validFrom").toString()), optionalInstant(values, "validUntil"),
                MemoryRetentionPolicy.valueOf(values.get("retentionPolicy").toString()),
                MemoryStatus.valueOf(values.get("status").toString()),
                ((Number) values.get("version")).intValue(), optionalUuid(values, "supersedesMemoryId"),
                optionalUuid(values, "supersededByMemoryId"), Instant.parse(values.get("createdAt").toString()),
                Instant.parse(values.get("updatedAt").toString()), optionalInstant(values, "deletedAt")
        );
    }

    private List<Provenance> readProvenance(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Provenance.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Proveniência de memória inválida persistida", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Proveniência de memória não serializável", exception);
        }
    }

    private static String optionalString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static UUID optionalUuid(Map<String, Object> values, String key) {
        String value = optionalString(values, key);
        return value == null ? null : UUID.fromString(value);
    }

    private static Instant optionalInstant(Map<String, Object> values, String key) {
        String value = optionalString(values, key);
        return value == null ? null : Instant.parse(value);
    }
}
