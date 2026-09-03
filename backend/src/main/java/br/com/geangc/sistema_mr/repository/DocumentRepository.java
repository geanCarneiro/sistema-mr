package br.com.geangc.sistema_mr.repository;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentChunk;
import br.com.geangc.sistema_mr.model.DocumentStatus;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRepository.class);
    private static final String VECTOR_INDEX_PREFIX = "grounding_chunk_embedding_";

    private final Driver driver;
    private final DocumentProperties properties;
    private final String vectorIndexName;

    public DocumentRepository(Driver driver, DocumentProperties properties) {
        this.driver = driver;
        this.properties = properties;
        this.vectorIndexName = VECTOR_INDEX_PREFIX + properties.embeddingDimensions();
    }

    @PostConstruct
    void initializeSchema() {
        List<String> statements = List.of(
                "CREATE CONSTRAINT grounding_context_id IF NOT EXISTS FOR (n:ContextoChat) REQUIRE n.id IS UNIQUE",
                "CREATE CONSTRAINT grounding_file_id IF NOT EXISTS FOR (n:Arquivo) REQUIRE n.id IS UNIQUE",
                "CREATE CONSTRAINT grounding_chunk_id IF NOT EXISTS FOR (n:Chunk) REQUIRE n.id IS UNIQUE",
                "CREATE INDEX grounding_file_owner IF NOT EXISTS FOR (n:Arquivo) ON (n.ownerSubject)",
                "CREATE VECTOR INDEX " + vectorIndexName + " IF NOT EXISTS "
                        + "FOR (n:Chunk) ON n.embedding OPTIONS {indexConfig: {"
                        + "`vector.dimensions`: " + properties.embeddingDimensions() + ", "
                        + "`vector.similarity_function`: 'cosine'}}"
        );

        try (var session = driver.session()) {
            for (String statement : statements) {
                session.run(statement).consume();
            }
        }
    }

    public ChatFile create(ChatFile file) {
        String query = """
                MERGE (context:ContextoChat {id: $conversationId})
                ON CREATE SET context.ownerSubject = $ownerSubject, context.createdAt = $createdAt
                CREATE (file:Arquivo {
                    id: $id,
                    conversationId: $conversationId,
                    ownerSubject: $ownerSubject,
                    originalName: $originalName,
                    mimeType: $mimeType,
                    size: $size,
                    sha256: $sha256,
                    originalStorageKey: $originalStorageKey,
                    status: $status,
                    contextTokenCount: 0,
                    embeddingModel: $embeddingModel,
                    createdAt: $createdAt,
                    updatedAt: $updatedAt
                })
                CREATE (context)-[:POSSUI]->(file)
                RETURN file
                """;

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", file.id().toString());
        parameters.put("conversationId", file.conversationId());
        parameters.put("ownerSubject", file.ownerSubject());
        parameters.put("originalName", file.originalName());
        parameters.put("mimeType", file.mimeType());
        parameters.put("size", file.size());
        parameters.put("sha256", file.sha256());
        parameters.put("originalStorageKey", file.originalStorageKey());
        parameters.put("status", file.status().name());
        parameters.put("embeddingModel", file.embeddingModel());
        parameters.put("createdAt", file.createdAt().toString());
        parameters.put("updatedAt", file.updatedAt().toString());

        try (var session = driver.session()) {
            return session.executeWrite(transaction -> mapFile(transaction.run(query, parameters).single(), "file"));
        }
    }

    public Optional<ChatFile> findById(UUID id) {
        return findOne("MATCH (file:Arquivo {id: $id}) "
                + "WHERE file.deletedAt IS NULL RETURN file", Map.of("id", id.toString()));
    }

    public Optional<ChatFile> findOwned(UUID id, String conversationId, String ownerSubject) {
        return findOne("""
                MATCH (:ContextoChat {id: $conversationId, ownerSubject: $ownerSubject})
                      -[:POSSUI]->(file:Arquivo {id: $id})
                WHERE file.deletedAt IS NULL
                RETURN file
                """, Map.of(
                "id", id.toString(),
                "conversationId", conversationId,
                "ownerSubject", ownerSubject
        ));
    }

    public List<ChatFile> listOwned(String conversationId, String ownerSubject) {
        String query = """
                MATCH (:ContextoChat {id: $conversationId, ownerSubject: $ownerSubject})
                      -[:POSSUI]->(file:Arquivo)
                WHERE file.deletedAt IS NULL
                RETURN file
                ORDER BY file.createdAt DESC
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "conversationId", conversationId,
                    "ownerSubject", ownerSubject
            )).list(record -> mapFile(record, "file")));
        }
    }

    public List<ChatFile> findPending() {
        String query = """
                MATCH (file:Arquivo)
                WHERE file.status IN ['QUEUED', 'EXTRACTING', 'EMBEDDING']
                  AND file.deletedAt IS NULL
                RETURN file
                ORDER BY file.createdAt
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query)
                    .list(record -> mapFile(record, "file")));
        }
    }

    public boolean hasReadyFiles(String conversationId, String ownerSubject) {
        String query = """
                MATCH (:ContextoChat {id: $conversationId, ownerSubject: $ownerSubject})
                      -[:POSSUI]->(file:Arquivo {status: 'READY'})
                WHERE file.deletedAt IS NULL
                RETURN count(file) > 0 AS present
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "conversationId", conversationId,
                    "ownerSubject", ownerSubject
            )).single().get("present").asBoolean());
        }
    }

    public List<ChatFile> findReadyOwnedByIds(
            Collection<UUID> ids,
            String conversationId,
            String ownerSubject
    ) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String query = """
                MATCH (:ContextoChat {id: $conversationId, ownerSubject: $ownerSubject})
                      -[:POSSUI]->(file:Arquivo {status: 'READY'})
                WHERE file.id IN $ids AND file.deletedAt IS NULL
                RETURN file
                ORDER BY file.createdAt
                """;
        List<String> stringIds = ids.stream().map(UUID::toString).toList();
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "conversationId", conversationId,
                    "ownerSubject", ownerSubject,
                    "ids", stringIds
            )).list(record -> mapFile(record, "file")));
        }
    }

    public Optional<ChatFile> resetForRetry(UUID id, String conversationId, String ownerSubject) {
        String query = """
                MATCH (:ContextoChat {id: $conversationId, ownerSubject: $ownerSubject})
                      -[:POSSUI]->(file:Arquivo {id: $id, status: 'FAILED'})
                WHERE file.deletedAt IS NULL
                OPTIONAL MATCH (file)-[:CONTEM]->(chunk:Chunk)
                DETACH DELETE chunk
                WITH file
                SET file.status = 'QUEUED',
                    file.errorMessage = null,
                    file.contextStorageKey = null,
                    file.updatedAt = $updatedAt
                RETURN file
                """;
        Map<String, Object> parameters = Map.of(
                "id", id.toString(),
                "conversationId", conversationId,
                "ownerSubject", ownerSubject,
                "updatedAt", Instant.now().toString()
        );
        try (var session = driver.session()) {
            return session.executeWrite(transaction -> {
                var result = transaction.run(query, parameters);
                return result.hasNext()
                        ? Optional.of(mapFile(result.next(), "file"))
                        : Optional.empty();
            });
        }
    }

    public void updateStatus(UUID id, DocumentStatus status, String errorMessage) {
        String query = """
                MATCH (file:Arquivo {id: $id})
                WHERE file.deletedAt IS NULL
                SET file.status = $status,
                    file.errorMessage = $errorMessage,
                    file.updatedAt = $updatedAt
                """;
        try (var session = driver.session()) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("id", id.toString());
            parameters.put("status", status.name());
            parameters.put("errorMessage", errorMessage);
            parameters.put("updatedAt", Instant.now().toString());
            session.executeWriteWithoutResult(transaction -> transaction.run(query, parameters).consume());
        }
    }

    public void markReady(
            UUID id,
            String contextStorageKey,
            String extractionMethod,
            String extractionWarnings,
            int contextTokenCount,
            List<DocumentChunk> chunks
    ) {
        String query = """
                MATCH (file:Arquivo {id: $id})
                WHERE file.deletedAt IS NULL
                OPTIONAL MATCH (file)-[:CONTEM]->(oldChunk:Chunk)
                DETACH DELETE oldChunk
                WITH file
                SET file.contextStorageKey = $contextStorageKey,
                    file.extractionMethod = $extractionMethod,
                    file.extractionWarnings = $extractionWarnings,
                    file.contextTokenCount = $contextTokenCount,
                    file.status = 'READY',
                    file.errorMessage = null,
                    file.updatedAt = $updatedAt
                WITH file
                UNWIND $chunks AS item
                CREATE (chunk:Chunk {
                    id: item.id,
                    position: item.position,
                    text: item.text,
                    embedding: item.embedding,
                    embeddingModel: $embeddingModel
                })
                CREATE (file)-[:CONTEM]->(chunk)
                """;

        List<Map<String, Object>> chunkParameters = chunks.stream().map(chunk -> Map.<String, Object>of(
                "id", chunk.id().toString(),
                "position", chunk.position(),
                "text", chunk.text(),
                "embedding", chunk.embedding()
        )).toList();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", id.toString());
        parameters.put("contextStorageKey", contextStorageKey);
        parameters.put("extractionMethod", extractionMethod);
        parameters.put("extractionWarnings", extractionWarnings);
        parameters.put("contextTokenCount", contextTokenCount);
        parameters.put("updatedAt", Instant.now().toString());
        parameters.put("embeddingModel", properties.embeddingModel());
        parameters.put("chunks", chunkParameters);

        try (var session = driver.session()) {
            session.executeWriteWithoutResult(transaction -> transaction.run(query, parameters).consume());
        }
    }

    public List<GroundingMatch> searchReadyFiles(
            String conversationId,
            String ownerSubject,
            List<Float> queryEmbedding
    ) {
        String indexedQuery = """
                CALL db.index.vector.queryNodes($indexName, $candidateCount, $embedding)
                YIELD node AS chunk, score
                MATCH (:ContextoChat {id: $conversationId, ownerSubject: $ownerSubject})
                      -[:POSSUI]->(file:Arquivo {status: 'READY'})-[:CONTEM]->(chunk)
                WHERE file.deletedAt IS NULL
                WITH file, max(score) AS score
                WHERE score >= $threshold
                RETURN file, score
                ORDER BY score DESC
                LIMIT $fileLimit
                """;

        Map<String, Object> parameters = Map.of(
                "indexName", vectorIndexName,
                "candidateCount", properties.retrievalCandidates(),
                "embedding", queryEmbedding,
                "conversationId", conversationId,
                "ownerSubject", ownerSubject,
                "threshold", properties.similarityThreshold(),
                "fileLimit", properties.retrievalFileLimit()
        );

        try {
            return executeSearch(indexedQuery, parameters);
        } catch (Neo4jException exception) {
            LOGGER.warn("Busca vetorial indexada indisponível; usando similaridade exata no escopo da conversa", exception);
            String exactQuery = """
                    MATCH (:ContextoChat {id: $conversationId, ownerSubject: $ownerSubject})
                          -[:POSSUI]->(file:Arquivo {status: 'READY'})-[:CONTEM]->(chunk:Chunk)
                    WHERE file.deletedAt IS NULL
                    WITH file, vector.similarity.cosine(chunk.embedding, $embedding) AS chunkScore
                    WITH file, max(chunkScore) AS score
                    WHERE score >= $threshold
                    RETURN file, score
                    ORDER BY score DESC
                    LIMIT $fileLimit
                    """;
            return executeSearch(exactQuery, parameters);
        }
    }

    public void deleteOwned(UUID id, String conversationId, String ownerSubject) {
        String query = """
                MATCH (:ContextoChat {id: $conversationId, ownerSubject: $ownerSubject})
                      -[:POSSUI]->(file:Arquivo {id: $id})
                WHERE file.deletedAt IS NULL
                OPTIONAL MATCH (file)-[:CONTEM]->(chunk:Chunk)
                DETACH DELETE chunk
                WITH file
                SET file.deletedAt = $deletedAt,
                    file.contextStorageKey = null,
                    file.updatedAt = $updatedAt
        """;
        try (var session = driver.session()) {
            session.executeWriteWithoutResult(transaction -> {
                String deletedAt = Instant.now().toString();
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("id", id.toString());
                parameters.put("conversationId", conversationId);
                parameters.put("ownerSubject", ownerSubject);
                parameters.put("deletedAt", deletedAt);
                parameters.put("updatedAt", deletedAt);
                transaction.run(query, parameters).consume();
            });
        }
    }

    private Optional<ChatFile> findOne(String query, Map<String, Object> parameters) {
        try (var session = driver.session()) {
            return session.executeRead(transaction -> {
                var result = transaction.run(query, parameters);
                return result.hasNext()
                        ? Optional.of(mapFile(result.next(), "file"))
                        : Optional.empty();
            });
        }
    }

    private List<GroundingMatch> executeSearch(String query, Map<String, Object> parameters) {
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, parameters).list(record ->
                    new GroundingMatch(mapFile(record, "file"), record.get("score").asDouble())
            ));
        }
    }

    private static ChatFile mapFile(Record record, String alias) {
        Map<String, Object> values = record.get(alias).asMap();
        return new ChatFile(
                UUID.fromString(string(values, "id")),
                string(values, "conversationId"),
                string(values, "ownerSubject"),
                string(values, "originalName"),
                string(values, "mimeType"),
                number(values, "size").longValue(),
                string(values, "sha256"),
                string(values, "originalStorageKey"),
                nullableString(values, "contextStorageKey"),
                DocumentStatus.valueOf(string(values, "status")),
                nullableString(values, "errorMessage"),
                number(values, "contextTokenCount").intValue(),
                nullableString(values, "embeddingModel"),
                Instant.parse(string(values, "createdAt")),
                Instant.parse(string(values, "updatedAt"))
        );
    }

    private static String string(Map<String, Object> values, String key) {
        return values.get(key).toString();
    }

    private static String nullableString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    private static Number number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number : 0;
    }

    public record GroundingMatch(ChatFile file, double score) {}
}
