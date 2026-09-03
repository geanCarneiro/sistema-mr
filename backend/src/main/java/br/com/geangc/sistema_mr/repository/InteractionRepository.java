package br.com.geangc.sistema_mr.repository;

import br.com.geangc.sistema_mr.model.Interaction;
import br.com.geangc.sistema_mr.model.InteractionSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;

@Repository
public class InteractionRepository {

    private final Driver driver;

    public InteractionRepository(Driver driver) {
        this.driver = driver;
    }

    @PostConstruct
    void initializeSchema() {
        try (var session = driver.session()) {
            session.run("CREATE CONSTRAINT interaction_id IF NOT EXISTS "
                    + "FOR (n:Interacao) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE INDEX interaction_owner IF NOT EXISTS "
                    + "FOR (n:Interacao) ON (n.ownerSubject)").consume();
            session.run("CREATE INDEX interaction_conversation IF NOT EXISTS "
                    + "FOR (n:Interacao) ON (n.conversationId)").consume();
        }
    }

    public void saveCompleted(Interaction interaction) {
        String upsertQuery = """
                MERGE (context:ContextoChat {id: $conversationId})
                ON CREATE SET context.ownerSubject = $ownerSubject,
                              context.createdAt = $createdAt
                WITH context
                WHERE context.ownerSubject = $ownerSubject
                MERGE (interaction:Interacao {id: $id})
                ON CREATE SET interaction.conversationId = $conversationId,
                              interaction.ownerSubject = $ownerSubject
                WITH context, interaction
                WHERE interaction.conversationId = $conversationId
                  AND interaction.ownerSubject = $ownerSubject
                SET interaction.userMessageId = $userMessageId,
                    interaction.assistantMessageId = $assistantMessageId,
                    interaction.prompt = $prompt,
                    interaction.response = $response,
                    interaction.conversationId = $conversationId,
                    interaction.ownerSubject = $ownerSubject,
                    interaction.createdAt = $createdAt,
                    interaction.completedAt = $completedAt
                MERGE (context)-[:POSSUI_INTERACAO]->(interaction)
                RETURN interaction
                """;

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", interaction.id().toString());
        parameters.put("userMessageId", interaction.userMessageId().toString());
        parameters.put("assistantMessageId", interaction.assistantMessageId().toString());
        parameters.put("prompt", interaction.prompt());
        parameters.put("response", interaction.response());
        parameters.put("conversationId", interaction.conversationId());
        parameters.put("ownerSubject", interaction.ownerSubject());
        parameters.put("createdAt", interaction.createdAt().toString());
        parameters.put("completedAt", interaction.completedAt().toString());

        try (var session = driver.session()) {
            session.executeWrite(transaction -> {
                var result = transaction.run(upsertQuery, parameters);
                if (!result.hasNext()) {
                    throw new IllegalArgumentException("O contexto da interação não pertence ao usuário");
                }
                result.consume();

                transaction.run("""
                        MATCH (interaction:Interacao {id: $id})
                        OPTIONAL MATCH (interaction)-[used:USOU_ARQUIVO]->(:Arquivo)
                        DELETE used
                        """, Map.of("id", interaction.id().toString())).consume();

                if (!interaction.sources().isEmpty()) {
                    List<Map<String, Object>> sources = interaction.sources().stream()
                            .map(source -> {
                                Map<String, Object> value = new HashMap<>();
                                value.put("fileId", source.fileId().toString());
                                value.put("sourceType", source.sourceType().name());
                                value.put("similarity", source.similarity());
                                return value;
                            })
                            .toList();

                    Map<String, Object> sourceParameters = new HashMap<>(parameters);
                    sourceParameters.put("sources", sources);
                    var sourceResult = transaction.run("""
                            MATCH (interaction:Interacao {
                                id: $id,
                                conversationId: $conversationId,
                                ownerSubject: $ownerSubject
                            })
                            UNWIND $sources AS source
                            MATCH (file:Arquivo {
                                id: source.fileId,
                                conversationId: $conversationId,
                                ownerSubject: $ownerSubject
                            })
                            MERGE (interaction)-[used:USOU_ARQUIVO]->(file)
                            SET used.sourceType = source.sourceType,
                                used.similarity = source.similarity
                            RETURN count(file) AS matched
                            """, sourceParameters);
                    long matched = sourceResult.single().get("matched").asLong();
                    if (matched != interaction.sources().size()) {
                        throw new IllegalStateException("Uma ou mais fontes da interação não estão disponíveis");
                    }
                }
                return null;
            });
        }
    }

    public List<Interaction> findOwned(String conversationId, String ownerSubject) {
        String query = """
                MATCH (:ContextoChat {
                    id: $conversationId,
                    ownerSubject: $ownerSubject
                })-[:POSSUI_INTERACAO]->(interaction:Interacao)
                OPTIONAL MATCH (interaction)-[used:USOU_ARQUIVO]->(file:Arquivo)
                RETURN interaction,
                       file.id AS fileId,
                       file.originalName AS fileName,
                       file.deletedAt AS fileDeletedAt,
                       used.sourceType AS sourceType,
                       used.similarity AS similarity
                ORDER BY interaction.createdAt ASC, file.originalName ASC
                """;

        try (var session = driver.session()) {
            return session.executeRead(transaction -> {
                Map<UUID, InteractionBuilder> interactions = new LinkedHashMap<>();
                transaction.run(query, Map.of(
                        "conversationId", conversationId,
                        "ownerSubject", ownerSubject
                )).forEachRemaining(record -> addRecord(interactions, record));
                return interactions.values().stream().map(InteractionBuilder::build).toList();
            });
        }
    }

    private static void addRecord(Map<UUID, InteractionBuilder> interactions, Record record) {
        Map<String, Object> values = record.get("interaction").asMap();
        UUID id = UUID.fromString(string(values, "id"));
        InteractionBuilder interaction = interactions.computeIfAbsent(id,
                ignored -> new InteractionBuilder(
                        id,
                        UUID.fromString(string(values, "userMessageId")),
                        UUID.fromString(string(values, "assistantMessageId")),
                        string(values, "prompt"),
                        string(values, "response"),
                        string(values, "conversationId"),
                        string(values, "ownerSubject"),
                        Instant.parse(string(values, "createdAt")),
                        Instant.parse(string(values, "completedAt"))
                ));

        if (!record.get("fileId").isNull()) {
            Double similarity = record.get("similarity").isNull()
                    ? null
                    : record.get("similarity").asDouble();
            String deletedAt = record.get("fileDeletedAt").isNull()
                    ? null
                    : record.get("fileDeletedAt").asString();
            interaction.sources.add(new InteractionSource(
                    UUID.fromString(record.get("fileId").asString()),
                    record.get("fileName").asString(),
                    InteractionSource.SourceType.valueOf(record.get("sourceType").asString()),
                    similarity,
                    deletedAt == null
            ));
        }
    }

    private static String string(Map<String, Object> values, String key) {
        return values.get(key).toString();
    }

    private static final class InteractionBuilder {
        private final UUID id;
        private final UUID userMessageId;
        private final UUID assistantMessageId;
        private final String prompt;
        private final String response;
        private final String conversationId;
        private final String ownerSubject;
        private final Instant createdAt;
        private final Instant completedAt;
        private final List<InteractionSource> sources = new ArrayList<>();

        private InteractionBuilder(
                UUID id,
                UUID userMessageId,
                UUID assistantMessageId,
                String prompt,
                String response,
                String conversationId,
                String ownerSubject,
                Instant createdAt,
                Instant completedAt
        ) {
            this.id = id;
            this.userMessageId = userMessageId;
            this.assistantMessageId = assistantMessageId;
            this.prompt = prompt;
            this.response = response;
            this.conversationId = conversationId;
            this.ownerSubject = ownerSubject;
            this.createdAt = createdAt;
            this.completedAt = completedAt;
        }

        private Interaction build() {
            return new Interaction(
                    id, userMessageId, assistantMessageId, prompt, response,
                    conversationId, ownerSubject, createdAt, completedAt, sources
            );
        }
    }
}
