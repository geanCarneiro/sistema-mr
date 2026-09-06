package br.com.geangc.sistema_mr.state.repository;

import br.com.geangc.sistema_mr.state.model.CurrentState;
import br.com.geangc.sistema_mr.state.model.CurrentStateProjection;
import br.com.geangc.sistema_mr.state.model.DynamicEntity;
import br.com.geangc.sistema_mr.state.model.Observation;
import br.com.geangc.sistema_mr.state.model.Provenance;
import br.com.geangc.sistema_mr.state.model.Specification;
import br.com.geangc.sistema_mr.state.model.StateChangeMetadata;
import br.com.geangc.sistema_mr.state.model.StatePatch;
import br.com.geangc.sistema_mr.state.model.StateTransition;
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
public class DynamicStateRepository {

    private final Driver driver;
    private final ObjectMapper objectMapper;

    public DynamicStateRepository(Driver driver, ObjectMapper objectMapper) {
        this.driver = driver;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initializeSchema() {
        try (var session = driver.session()) {
            session.run("CREATE CONSTRAINT dynamic_entity_id IF NOT EXISTS "
                    + "FOR (n:Entidade) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT state_observation_id IF NOT EXISTS "
                    + "FOR (n:Observacao) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT state_specification_id IF NOT EXISTS "
                    + "FOR (n:Especificacao) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT state_transition_id IF NOT EXISTS "
                    + "FOR (n:TransicaoEstado) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT current_state_id IF NOT EXISTS "
                    + "FOR (n:EstadoAtual) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE INDEX dynamic_entity_owner_context IF NOT EXISTS "
                    + "FOR (n:Entidade) ON (n.ownerSubject, n.contextId)").consume();
            session.run("CREATE INDEX state_transition_idempotency IF NOT EXISTS "
                    + "FOR (n:TransicaoEstado) ON (n.idempotencyKey)").consume();
        }
    }

    public DynamicEntity create(
            DynamicEntity entity,
            Map<String, Object> initialPayload,
            StateChangeMetadata metadata
    ) {
        String now = entity.updatedAt().toString();
        String query = """
                MERGE (context:ContextoChat {id: $contextId})
                ON CREATE SET context.ownerSubject = $ownerSubject,
                              context.createdAt = $createdAt
                WITH context
                WHERE context.ownerSubject = $ownerSubject
                CREATE (entity:Entidade {
                    id: $entityId,
                    ownerSubject: $ownerSubject,
                    contextId: $contextId,
                    type: $type,
                    createdAt: $createdAt,
                    updatedAt: $updatedAt
                })
                CREATE (current:EstadoAtual {
                    id: $entityId,
                    entityId: $entityId,
                    version: 1,
                    payloadJson: $afterPayloadJson,
                    provenanceJson: $provenanceJson,
                    confidence: $confidence,
                    updatedAt: $updatedAt
                })
                CREATE (entity)-[:POSSUI_ESTADO_ATUAL]->(current)
                CREATE (transition:TransicaoEstado {
                    id: $transitionId,
                    entityId: $entityId,
                    ownerSubject: $ownerSubject,
                    contextId: $contextId,
                    fromVersion: 0,
                    toVersion: 1,
                    operation: 'CREATE',
                    beforePayloadJson: $beforePayloadJson,
                    afterPayloadJson: $afterPayloadJson,
                    changesJson: $changesJson,
                    origin: $origin,
                    confidence: $confidence,
                    reason: $reason,
                    provenanceJson: $provenanceJson,
                    idempotencyKey: $idempotencyKey,
                    occurredAt: $occurredAt
                })
                CREATE (entity)-[:POSSUI_TRANSICAO]->(transition)
                RETURN entity
                """;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("entityId", entity.id().toString());
        parameters.put("ownerSubject", entity.ownerSubject());
        parameters.put("contextId", entity.contextId());
        parameters.put("type", entity.type());
        parameters.put("createdAt", entity.createdAt().toString());
        parameters.put("updatedAt", now);
        parameters.put("afterPayloadJson", writeJson(initialPayload));
        parameters.put("beforePayloadJson", "{}");
        parameters.put("changesJson", writeJson(Map.of("set", initialPayload.keySet(), "remove", List.of())));
        parameters.put("provenanceJson", writeJson(metadata.provenance()));
        parameters.put("confidence", metadata.confidence());
        parameters.put("origin", metadata.origin().name());
        parameters.put("reason", metadata.reason());
        parameters.put("idempotencyKey", metadata.idempotencyKey());
        parameters.put("occurredAt", metadata.occurredAt().toString());
        parameters.put("transitionId", UUID.randomUUID().toString());

        try (var session = driver.session()) {
            return session.executeWrite(transaction -> {
                var result = transaction.run(query, parameters);
                if (!result.hasNext()) {
                    throw new IllegalArgumentException("O contexto da entidade não pertence ao usuário");
                }
                return mapEntity(result.single());
            });
        }
    }

    public Observation saveObservation(Observation observation) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE]->(entity:Entidade {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })
                CREATE (observation:Observacao {
                    id: $observationId,
                    entityId: $entityId,
                    ownerSubject: $ownerSubject,
                    contextId: $contextId,
                    type: $type,
                    payloadJson: $payloadJson,
                    origin: $origin,
                    confidence: $confidence,
                    provenanceJson: $provenanceJson,
                    observedAt: $observedAt
                })
                CREATE (entity)-[:POSSUI_OBSERVACAO]->(observation)
                RETURN observation
                """;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("contextId", observation.contextId());
        parameters.put("ownerSubject", observation.ownerSubject());
        parameters.put("entityId", observation.entityId().toString());
        parameters.put("observationId", observation.id().toString());
        parameters.put("type", observation.type());
        parameters.put("payloadJson", writeJson(observation.payload()));
        parameters.put("origin", observation.origin().name());
        parameters.put("confidence", observation.confidence());
        parameters.put("provenanceJson", writeJson(observation.provenance()));
        parameters.put("observedAt", observation.observedAt().toString());
        executeRequired(query, parameters, "A entidade da observação não pertence ao usuário");
        return observation;
    }

    public Optional<CurrentState> findCurrent(UUID entityId, String ownerSubject, String contextId) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE]->(entity:Entidade {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_ESTADO_ATUAL]->(current:EstadoAtual)
                RETURN current.entityId AS entityId,
                       current.version AS version,
                       current.payloadJson AS payloadJson,
                       current.provenanceJson AS provenanceJson,
                       current.confidence AS confidence,
                       current.updatedAt AS updatedAt
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "entityId", entityId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId
            )).stream().findFirst().map(this::mapCurrent));
        }
    }

    public Optional<CurrentStateProjection> findCurrentProjection(
            UUID entityId,
            String ownerSubject,
            String contextId
    ) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE]->(entity:Entidade {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_ESTADO_ATUAL]->(current:EstadoAtual)
                RETURN entity.id AS entityId,
                       entity.type AS type,
                       current.version AS version,
                       current.payloadJson AS payloadJson,
                       current.provenanceJson AS provenanceJson,
                       current.confidence AS confidence,
                       current.updatedAt AS updatedAt
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "entityId", entityId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId
            )).stream().findFirst().map(this::mapProjection));
        }
    }

    public List<StateTransition> findTransitions(UUID entityId, String ownerSubject, String contextId) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE]->(entity:Entidade {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_TRANSICAO]->(transition:TransicaoEstado)
                RETURN transition
                ORDER BY transition.toVersion ASC
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "entityId", entityId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId
            )).list(this::mapTransition));
        }
    }

    public List<Observation> findObservations(UUID entityId, String ownerSubject, String contextId) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE]->(entity:Entidade {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_OBSERVACAO]->(observation:Observacao)
                RETURN observation
                ORDER BY observation.observedAt ASC
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "entityId", entityId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId
            )).list(this::mapObservation));
        }
    }

    public Optional<Specification> findLatestSpecification(UUID entityId, String ownerSubject, String contextId) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE]->(entity:Entidade {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_ESPECIFICACAO]->(specification:Especificacao)
                RETURN specification
                ORDER BY specification.version DESC
                LIMIT 1
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "entityId", entityId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId
            )).stream().findFirst().map(this::mapSpecification));
        }
    }

    public StateChangeResult applyPatch(
            UUID entityId,
            String ownerSubject,
            String contextId,
            int expectedVersion,
            String beforePayloadJson,
            String afterPayloadJson,
            String changesJson,
            StateChangeMetadata metadata
    ) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE]->(entity:Entidade {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_ESTADO_ATUAL]->(current:EstadoAtual)
                OPTIONAL MATCH (entity)-[:POSSUI_TRANSICAO]->(duplicate:TransicaoEstado {
                    idempotencyKey: $idempotencyKey
                })
                WITH entity, current, duplicate,
                     CASE WHEN duplicate IS NOT NULL THEN 'DUPLICATE'
                          WHEN current.version = $expectedVersion THEN 'APPLIED'
                          ELSE 'CONFLICT' END AS outcome
                FOREACH (_ IN CASE WHEN outcome = 'APPLIED' THEN [1] ELSE [] END |
                    SET current.payloadJson = $afterPayloadJson,
                        current.provenanceJson = $provenanceJson,
                        current.confidence = $confidence,
                        current.version = current.version + 1,
                        current.updatedAt = $updatedAt,
                        entity.updatedAt = $updatedAt
                    CREATE (transition:TransicaoEstado {
                        id: $transitionId,
                        entityId: $entityId,
                        ownerSubject: $ownerSubject,
                        contextId: $contextId,
                        fromVersion: $expectedVersion,
                        toVersion: $expectedVersion + 1,
                        operation: 'PATCH',
                        beforePayloadJson: $beforePayloadJson,
                        afterPayloadJson: $afterPayloadJson,
                        changesJson: $changesJson,
                        origin: $origin,
                        confidence: $confidence,
                        reason: $reason,
                        provenanceJson: $provenanceJson,
                        idempotencyKey: $idempotencyKey,
                        occurredAt: $occurredAt
                    })
                    CREATE (entity)-[:POSSUI_TRANSICAO]->(transition)
                )
                RETURN outcome,
                       current.entityId AS entityId,
                       current.version AS version,
                       current.payloadJson AS payloadJson,
                       current.provenanceJson AS provenanceJson,
                       current.confidence AS confidence,
                       current.updatedAt AS updatedAt
                """;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("entityId", entityId.toString());
        parameters.put("ownerSubject", ownerSubject);
        parameters.put("contextId", contextId);
        parameters.put("expectedVersion", expectedVersion);
        parameters.put("beforePayloadJson", beforePayloadJson);
        parameters.put("afterPayloadJson", afterPayloadJson);
        parameters.put("changesJson", changesJson);
        parameters.put("provenanceJson", writeJson(metadata.provenance()));
        parameters.put("confidence", metadata.confidence());
        parameters.put("updatedAt", metadata.occurredAt().toString());
        parameters.put("transitionId", UUID.randomUUID().toString());
        parameters.put("origin", metadata.origin().name());
        parameters.put("reason", metadata.reason());
        parameters.put("idempotencyKey", metadata.idempotencyKey());
        parameters.put("occurredAt", metadata.occurredAt().toString());

        try (var session = driver.session()) {
            return session.executeWrite(transaction -> transaction.run(query, parameters).stream()
                    .findFirst()
                    .map(record -> new StateChangeResult(
                            record.get("outcome").asString(),
                            mapCurrent(record)
                    ))
                    .orElseThrow(() -> new IllegalArgumentException("A entidade não pertence ao usuário")));
        }
    }

    public Specification saveSpecification(Specification specification) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE]->(entity:Entidade {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })
                CREATE (specification:Especificacao {
                    id: $specificationId,
                    entityId: $entityId,
                    ownerSubject: $ownerSubject,
                    contextId: $contextId,
                    version: $version,
                    payloadJson: $payloadJson,
                    provenanceJson: $provenanceJson,
                    createdAt: $createdAt
                })
                CREATE (entity)-[:POSSUI_ESPECIFICACAO]->(specification)
                RETURN specification
                """;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("contextId", specification.contextId());
        parameters.put("ownerSubject", specification.ownerSubject());
        parameters.put("entityId", specification.entityId().toString());
        parameters.put("specificationId", specification.id().toString());
        parameters.put("version", specification.version());
        parameters.put("payloadJson", writeJson(specification.payload()));
        parameters.put("provenanceJson", writeJson(specification.provenance()));
        parameters.put("createdAt", specification.createdAt().toString());
        executeRequired(query, parameters, "A entidade da especificação não pertence ao usuário");
        return specification;
    }

    private void executeRequired(String query, Map<String, Object> parameters, String message) {
        try (var session = driver.session()) {
            session.executeWrite(transaction -> {
                var result = transaction.run(query, parameters);
                if (!result.hasNext()) {
                    throw new IllegalArgumentException(message);
                }
                result.consume();
                return null;
            });
        }
    }

    private DynamicEntity mapEntity(Record record) {
        Map<String, Object> values = record.get("entity").asMap();
        return new DynamicEntity(
                UUID.fromString(values.get("id").toString()),
                values.get("ownerSubject").toString(),
                values.get("contextId").toString(),
                values.get("type").toString(),
                Instant.parse(values.get("createdAt").toString()),
                Instant.parse(values.get("updatedAt").toString())
        );
    }

    private CurrentState mapCurrent(Record record) {
        return new CurrentState(
                UUID.fromString(record.get("entityId").asString()),
                record.get("version").asInt(),
                readObject(record.get("payloadJson").asString()),
                readProvenance(record.get("provenanceJson").asString()),
                record.get("confidence").asDouble(),
                Instant.parse(record.get("updatedAt").asString())
        );
    }

    private CurrentStateProjection mapProjection(Record record) {
        return new CurrentStateProjection(
                UUID.fromString(record.get("entityId").asString()),
                record.get("type").asString(),
                record.get("version").asInt(),
                readObject(record.get("payloadJson").asString()),
                readProvenance(record.get("provenanceJson").asString()),
                record.get("confidence").asDouble(),
                Instant.parse(record.get("updatedAt").asString())
        );
    }

    private StateTransition mapTransition(Record record) {
        Map<String, Object> values = record.get("transition").asMap();
        return new StateTransition(
                UUID.fromString(values.get("id").toString()),
                UUID.fromString(values.get("entityId").toString()),
                values.get("ownerSubject").toString(),
                values.get("contextId").toString(),
                ((Number) values.get("fromVersion")).intValue(),
                ((Number) values.get("toVersion")).intValue(),
                values.get("operation").toString(),
                readObject(values.get("beforePayloadJson").toString()),
                readObject(values.get("afterPayloadJson").toString()),
                readObject(values.get("changesJson").toString()),
                br.com.geangc.sistema_mr.state.model.StateChangeOrigin.valueOf(values.get("origin").toString()),
                ((Number) values.get("confidence")).doubleValue(),
                values.get("reason").toString(),
                readProvenance(values.get("provenanceJson").toString()),
                values.get("idempotencyKey").toString(),
                Instant.parse(values.get("occurredAt").toString())
        );
    }

    private Observation mapObservation(Record record) {
        Map<String, Object> values = record.get("observation").asMap();
        return new Observation(
                UUID.fromString(values.get("id").toString()),
                UUID.fromString(values.get("entityId").toString()),
                values.get("ownerSubject").toString(),
                values.get("contextId").toString(),
                values.get("type").toString(),
                readObject(values.get("payloadJson").toString()),
                br.com.geangc.sistema_mr.state.model.StateChangeOrigin.valueOf(values.get("origin").toString()),
                ((Number) values.get("confidence")).doubleValue(),
                readProvenance(values.get("provenanceJson").toString()),
                Instant.parse(values.get("observedAt").toString())
        );
    }

    private Specification mapSpecification(Record record) {
        Map<String, Object> values = record.get("specification").asMap();
        return new Specification(
                UUID.fromString(values.get("id").toString()),
                UUID.fromString(values.get("entityId").toString()),
                values.get("ownerSubject").toString(),
                values.get("contextId").toString(),
                ((Number) values.get("version")).intValue(),
                readObject(values.get("payloadJson").toString()),
                readProvenance(values.get("provenanceJson").toString()),
                Instant.parse(values.get("createdAt").toString())
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObject(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Payload de estado inválido persistido", exception);
        }
    }

    private List<Provenance> readProvenance(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Provenance.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Proveniência inválida persistida", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Payload dinâmico não serializável", exception);
        }
    }

    public record StateChangeResult(String outcome, CurrentState currentState) {
        public boolean applied() {
            return "APPLIED".equals(outcome);
        }

        public boolean duplicate() {
            return "DUPLICATE".equals(outcome);
        }
    }
}
