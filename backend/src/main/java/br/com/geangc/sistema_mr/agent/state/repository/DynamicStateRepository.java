package br.com.geangc.sistema_mr.agent.state.repository;

import br.com.geangc.sistema_mr.agent.state.DynamicPayload;
import br.com.geangc.sistema_mr.agent.state.StateIdempotencyConflictException;
import br.com.geangc.sistema_mr.agent.state.StateVersionConflictException;
import br.com.geangc.sistema_mr.agent.state.model.DynamicEntity;
import br.com.geangc.sistema_mr.agent.state.model.EntitySpecification;
import br.com.geangc.sistema_mr.agent.state.model.EntityState;
import br.com.geangc.sistema_mr.agent.state.model.StateObservation;
import br.com.geangc.sistema_mr.agent.state.model.StateProvenance;
import br.com.geangc.sistema_mr.agent.state.model.StateProjection;
import br.com.geangc.sistema_mr.agent.state.model.StateTransition;
import br.com.geangc.sistema_mr.agent.state.model.StateTransitionCommand;
import br.com.geangc.sistema_mr.agent.state.model.TransitionResult;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Repository;
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
                    + "FOR (n:EntidadeEstado) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT state_transition_id IF NOT EXISTS "
                    + "FOR (n:EstadoTransicao) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT state_observation_id IF NOT EXISTS "
                    + "FOR (n:ObservacaoEstado) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT state_history_id IF NOT EXISTS "
                    + "FOR (n:EstadoHistorico) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT state_version_claim_id IF NOT EXISTS "
                    + "FOR (n:EstadoVersao) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT state_specification_id IF NOT EXISTS "
                    + "FOR (n:EspecificacaoEstado) REQUIRE n.id IS UNIQUE").consume();
            session.run("CREATE INDEX dynamic_entity_owner IF NOT EXISTS "
                    + "FOR (n:EntidadeEstado) ON (n.ownerSubject)").consume();
            session.run("CREATE INDEX dynamic_entity_context IF NOT EXISTS "
                    + "FOR (n:EntidadeEstado) ON (n.contextId)").consume();
            session.run("CREATE INDEX state_transition_entity IF NOT EXISTS "
                    + "FOR (n:EstadoTransicao) ON (n.entityId)").consume();
        }
    }

    public DynamicEntity createEntity(DynamicEntity entity) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                MERGE (entity:EntidadeEstado {id: $entityId})
                ON CREATE SET entity.ownerSubject = $ownerSubject,
                              entity.contextId = $contextId,
                              entity.type = $type,
                              entity.createdAt = $createdAt
                WITH context, entity
                WHERE entity.ownerSubject = $ownerSubject
                  AND entity.contextId = $contextId
                MERGE (context)-[:POSSUI_ENTIDADE_ESTADO]->(entity)
                MERGE (entity)-[:POSSUI_ESTADO_ATUAL]->(current:EstadoAtual)
                ON CREATE SET current.id = $currentId,
                              current.version = 0,
                              current.payloadJson = '{}',
                              current.origin = 'SYSTEM',
                              current.confidence = 1.0,
                              current.reason = 'Estado inicial',
                              current.updatedAt = $createdAt,
                              current.sourceType = 'SYSTEM',
                              current.sourceId = '',
                              current.sourceVersion = ''
                WITH context, entity, current
                MERGE (current)-[:POSSUI_CAMPO]->(field:EstadoCampo {path: ''})
                ON CREATE SET field.id = $rootFieldId, field.valueJson = '{}'
                RETURN entity
                """;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("contextId", entity.contextId());
        parameters.put("ownerSubject", entity.ownerSubject());
        parameters.put("entityId", entity.id().toString());
        parameters.put("type", entity.type());
        parameters.put("createdAt", entity.createdAt().toString());
        parameters.put("currentId", UUID.randomUUID().toString());
        parameters.put("rootFieldId", UUID.randomUUID().toString());

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

    public Optional<DynamicEntity> findEntity(UUID entityId, String ownerSubject, String contextId) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE_ESTADO]->(entity:EntidadeEstado {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })
                RETURN entity
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "entityId", entityId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId
            )).stream().findFirst().map(this::mapEntity));
        }
    }

    public Optional<EntityState> findCurrentState(UUID entityId, String ownerSubject, String contextId) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE_ESTADO]->(entity:EntidadeEstado {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_ESTADO_ATUAL]->(current:EstadoAtual)
                RETURN entity, current
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "entityId", entityId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId
            )).stream().findFirst().map(this::mapCurrentState));
        }
    }

    public StateProjection projectCurrentState(
            UUID entityId,
            String ownerSubject,
            String contextId,
            List<String> paths
    ) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE_ESTADO]->(entity:EntidadeEstado {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_ESTADO_ATUAL]->(current:EstadoAtual)
                MATCH (current)-[:POSSUI_CAMPO]->(field:EstadoCampo)
                WHERE size($paths) = 0 OR field.path IN $paths
                RETURN current.version AS version, collect(field) AS fields
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "entityId", entityId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId,
                    "paths", paths
            )).stream().findFirst().map(this::mapProjection)
                    .orElseThrow(() -> new IllegalArgumentException("Entidade dinâmica não encontrada")));
        }
    }

    public TransitionResult applyStateTransition(StateTransitionCommand command) {
        String payloadJson = DynamicPayload.toJson(command.payload(), objectMapper);
        String fingerprint = fingerprint(command, payloadJson);
        UUID transitionId = transitionId(command.entityId(), command.idempotencyKey());

        Optional<StateTransition> existing = findTransition(transitionId, command.ownerSubject(), command.contextId());
        if (existing.isPresent()) {
            return existing.get().fingerprint().equals(fingerprint)
                    ? new TransitionResult(existing.get(), true)
                    : throwIdempotencyConflict(command.idempotencyKey());
        }

        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE_ESTADO]->(entity:EntidadeEstado {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })
                MATCH (entity)-[:POSSUI_ESTADO_ATUAL]->(current:EstadoAtual)
                WHERE current.version = $expectedVersion
                CREATE (claim:EstadoVersao {
                    id: $claimId,
                    entityId: $entityId,
                    ownerSubject: $ownerSubject,
                    contextId: $contextId,
                    version: $expectedVersion,
                    claimedAt: $occurredAt
                })
                SET current.version = $toVersion
                MERGE (transition:EstadoTransicao {id: $transitionId})
                ON CREATE SET transition.entityId = $entityId,
                              transition.ownerSubject = $ownerSubject,
                              transition.contextId = $contextId,
                              transition.fromVersion = $expectedVersion,
                              transition.toVersion = $toVersion,
                              transition.beforePayloadJson = current.payloadJson,
                              transition.afterPayloadJson = $payloadJson,
                              transition.origin = $origin,
                              transition.confidence = $confidence,
                              transition.reason = $reason,
                              transition.occurredAt = $occurredAt,
                              transition.idempotencyKey = $idempotencyKey,
                              transition.fingerprint = $fingerprint,
                              transition.sourceType = $sourceType,
                              transition.sourceId = $sourceId,
                              transition.sourceVersion = $sourceVersion,
                              transition.completed = false
                WITH context, entity, current, transition
                WHERE transition.completed = false
                  AND transition.fingerprint = $fingerprint
                CREATE (history:EstadoHistorico {
                    id: $historyId,
                    entityId: $entityId,
                    version: $toVersion,
                    payloadJson: $payloadJson,
                    origin: $origin,
                    confidence: $confidence,
                    reason: $reason,
                    occurredAt: $occurredAt,
                    sourceType: $sourceType,
                    sourceId: $sourceId,
                    sourceVersion: $sourceVersion
                })
                CREATE (transition)-[:PRODUZ_ESTADO]->(history)
                CREATE (entity)-[:POSSUI_TRANSICAO]->(transition)
                WITH context, entity, current, transition, history
                OPTIONAL MATCH (current)-[oldRelation:POSSUI_CAMPO]->(oldField:EstadoCampo)
                DELETE oldRelation, oldField
                WITH DISTINCT context, entity, current, transition, history
                UNWIND $fields AS field
                CREATE (current)-[:POSSUI_CAMPO]->(:EstadoCampo {
                    id: field.id,
                    path: field.path,
                    valueJson: field.valueJson
                })
                WITH context, entity, current, transition, history
                SET current.version = $toVersion,
                    current.payloadJson = $payloadJson,
                    current.origin = $origin,
                    current.confidence = $confidence,
                    current.reason = $reason,
                    current.updatedAt = $occurredAt,
                    current.transitionId = $transitionId,
                    current.sourceType = $sourceType,
                    current.sourceId = $sourceId,
                    current.sourceVersion = $sourceVersion,
                    transition.completed = true
                WITH context, entity, current, transition, history
                OPTIONAL MATCH (file:Arquivo {
                    id: $sourceId,
                    conversationId: $contextId,
                    ownerSubject: $ownerSubject
                })
                WITH transition, history, current, collect(file) AS files
                FOREACH (matchedFile IN files |
                    MERGE (transition)-[:DERIVADO_DE]->(matchedFile)
                )
                RETURN transition, history, current
                """;
        Map<String, Object> parameters = transitionParameters(command, payloadJson, fingerprint, transitionId);
        List<Map<String, Object>> fields = new ArrayList<>();
        DynamicPayload.fields(command.payload(), objectMapper).forEach((path, valueJson) -> {
            fields.add(Map.of(
                    "id", UUID.randomUUID().toString(),
                    "path", path,
                    "valueJson", valueJson
            ));
        });
        parameters.put("fields", fields);
        parameters.put("historyId", UUID.randomUUID().toString());
        parameters.put("claimId", command.entityId() + ":" + command.expectedVersion());

        try (var session = driver.session()) {
            try {
                Optional<TransitionResult> written = session.executeWrite(transaction -> {
                    var result = transaction.run(query, parameters);
                    return result.hasNext()
                            ? Optional.of(new TransitionResult(mapTransition(result.single()), false))
                            : Optional.empty();
                });
                if (written.isPresent()) {
                    return written.get();
                }
            } catch (Neo4jException exception) {
                if (!exception.code().contains("ConstraintValidationFailed")) {
                    throw exception;
                }
            }
        }

        existing = findTransition(transitionId, command.ownerSubject(), command.contextId());
        if (existing.isPresent()) {
            return existing.get().fingerprint().equals(fingerprint)
                    ? new TransitionResult(existing.get(), true)
                    : throwIdempotencyConflict(command.idempotencyKey());
        }
        long currentVersion = findCurrentState(command.entityId(), command.ownerSubject(), command.contextId())
                .map(EntityState::version)
                .orElseThrow(() -> new IllegalArgumentException("Entidade dinâmica não encontrada"));
        throw new StateVersionConflictException(command.expectedVersion(), currentVersion);
    }

    public StateObservation recordObservation(StateObservation observation, String contextId, String ownerSubject) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE_ESTADO]->(entity:EntidadeEstado {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })
                MERGE (observation:ObservacaoEstado {id: $observationId})
                ON CREATE SET observation.entityId = $entityId,
                              observation.ownerSubject = $ownerSubject,
                              observation.contextId = $contextId,
                              observation.payloadJson = $payloadJson,
                              observation.origin = $origin,
                              observation.confidence = $confidence,
                              observation.observedAt = $observedAt,
                              observation.sourceType = $sourceType,
                              observation.sourceId = $sourceId,
                              observation.sourceVersion = $sourceVersion
                WITH context, entity, observation
                WHERE observation.entityId = $entityId
                  AND observation.ownerSubject = $ownerSubject
                  AND observation.contextId = $contextId
                MERGE (entity)-[:POSSUI_OBSERVACAO]->(observation)
                OPTIONAL MATCH (file:Arquivo {
                    id: $sourceId,
                    conversationId: $contextId,
                    ownerSubject: $ownerSubject
                })
                WITH context, entity, observation, collect(file) AS files
                FOREACH (matchedFile IN files |
                    MERGE (observation)-[:DERIVADO_DE]->(matchedFile)
                )
                RETURN observation
                """;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("contextId", contextId);
        parameters.put("ownerSubject", ownerSubject);
        parameters.put("entityId", observation.entityId().toString());
        parameters.put("observationId", observation.id().toString());
        parameters.put("payloadJson", DynamicPayload.toJson(observation.payload(), objectMapper));
        parameters.put("origin", observation.origin());
        parameters.put("confidence", observation.confidence());
        parameters.put("observedAt", observation.observedAt().toString());
        putProvenance(parameters, observation.provenance());

        try (var session = driver.session()) {
            return session.executeWrite(transaction -> {
                var result = transaction.run(query, parameters);
                if (!result.hasNext()) {
                    throw new IllegalArgumentException("A observação não pertence ao usuário e contexto");
                }
                return mapObservation(result.single());
            });
        }
    }

    public Optional<EntitySpecification> findSpecification(UUID entityId, String ownerSubject, String contextId) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE_ESTADO]->(entity:EntidadeEstado {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_ESPECIFICACAO]->(spec:EspecificacaoEstado)
                RETURN spec
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "entityId", entityId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId
            )).stream().findFirst().map(this::mapSpecification));
        }
    }

    public EntitySpecification saveSpecification(
            UUID entityId,
            String ownerSubject,
            String contextId,
            long expectedVersion,
            Map<String, Object> payload,
            StateProvenance provenance,
            Instant updatedAt
    ) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE_ESTADO]->(entity:EntidadeEstado {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })
                MERGE (entity)-[:POSSUI_ESPECIFICACAO]->(spec:EspecificacaoEstado)
                ON CREATE SET spec.id = $specId,
                              spec.entityId = $entityId,
                              spec.version = 0,
                              spec.payloadJson = '{}',
                              spec.sourceType = 'SYSTEM',
                              spec.sourceId = '',
                              spec.sourceVersion = ''
                WITH context, entity, spec
                WHERE spec.version = $expectedVersion
                SET spec.version = $nextVersion,
                    spec.payloadJson = $payloadJson,
                    spec.updatedAt = $updatedAt,
                    spec.sourceType = $sourceType,
                    spec.sourceId = $sourceId,
                    spec.sourceVersion = $sourceVersion
                CREATE (history:EspecificacaoHistorico {
                    id: $historyId,
                    entityId: $entityId,
                    version: $nextVersion,
                    payloadJson: $payloadJson,
                    updatedAt: $updatedAt,
                    sourceType: $sourceType,
                    sourceId: $sourceId,
                    sourceVersion: $sourceVersion
                })
                CREATE (spec)-[:POSSUI_VERSAO]->(history)
                RETURN spec
                """;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("contextId", contextId);
        parameters.put("ownerSubject", ownerSubject);
        parameters.put("entityId", entityId.toString());
        parameters.put("specId", UUID.randomUUID().toString());
        parameters.put("expectedVersion", expectedVersion);
        parameters.put("nextVersion", expectedVersion + 1);
        parameters.put("payloadJson", DynamicPayload.toJson(payload, objectMapper));
        parameters.put("updatedAt", updatedAt.toString());
        parameters.put("historyId", UUID.randomUUID().toString());
        putProvenance(parameters, provenance);

        try (var session = driver.session()) {
            Optional<EntitySpecification> written = session.executeWrite(transaction -> {
                var result = transaction.run(query, parameters);
                return result.hasNext()
                        ? Optional.of(mapSpecification(result.single()))
                        : Optional.empty();
            });
            if (written.isPresent()) {
                return written.get();
            }
        }
        long currentVersion = findSpecification(entityId, ownerSubject, contextId)
                .map(EntitySpecification::version).orElse(0L);
        throw new StateVersionConflictException(expectedVersion, currentVersion);
    }

    public List<StateTransition> findTransitionHistory(UUID entityId, String ownerSubject, String contextId) {
        String query = """
                MATCH (context:ContextoChat {id: $contextId, ownerSubject: $ownerSubject})
                      -[:POSSUI_ENTIDADE_ESTADO]->(entity:EntidadeEstado {
                          id: $entityId,
                          ownerSubject: $ownerSubject,
                          contextId: $contextId
                      })-[:POSSUI_TRANSICAO]->(transition:EstadoTransicao)
                WHERE transition.completed = true
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

    private Optional<StateTransition> findTransition(UUID transitionId, String ownerSubject, String contextId) {
        String query = """
                MATCH (transition:EstadoTransicao {
                    id: $transitionId,
                    ownerSubject: $ownerSubject,
                    contextId: $contextId,
                    completed: true
                })
                RETURN transition
                """;
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction.run(query, Map.of(
                    "transitionId", transitionId.toString(),
                    "ownerSubject", ownerSubject,
                    "contextId", contextId
            )).stream().findFirst().map(this::mapTransition));
        }
    }

    private StateProjection mapProjection(Record record) {
        Map<String, Object> projection = new LinkedHashMap<>();
        for (Object item : record.get("fields").asList()) {
            Map<String, Object> field = ((org.neo4j.driver.types.Node) item).asMap();
            projection.put(
                    field.get("path").toString(),
                    DynamicPayload.toMapValue(field.get("valueJson").toString(), objectMapper)
            );
        }
        return new StateProjection(record.get("version").asLong(), projection);
    }

    private EntityState mapCurrentState(Record record) {
        Map<String, Object> values = record.get("current").asMap();
        return new EntityState(
                UUID.fromString(record.get("entity").asMap().get("id").toString()),
                number(values, "version").longValue(),
                DynamicPayload.toMap(string(values, "payloadJson"), objectMapper),
                string(values, "origin"),
                number(values, "confidence").doubleValue(),
                string(values, "reason"),
                Instant.parse(string(values, "updatedAt")),
                provenance(values),
                optionalUuid(values, "transitionId")
        );
    }

    private DynamicEntity mapEntity(Record record) {
        Map<String, Object> values = record.get("entity").asMap();
        return new DynamicEntity(
                UUID.fromString(string(values, "id")),
                string(values, "ownerSubject"),
                string(values, "contextId"),
                string(values, "type"),
                Instant.parse(string(values, "createdAt"))
        );
    }

    private StateTransition mapTransition(Record record) {
        Map<String, Object> values = record.get("transition").asMap();
        return new StateTransition(
                UUID.fromString(string(values, "id")),
                UUID.fromString(string(values, "entityId")),
                number(values, "fromVersion").longValue(),
                number(values, "toVersion").longValue(),
                DynamicPayload.toMap(string(values, "beforePayloadJson"), objectMapper),
                DynamicPayload.toMap(string(values, "afterPayloadJson"), objectMapper),
                string(values, "origin"),
                number(values, "confidence").doubleValue(),
                string(values, "reason"),
                Instant.parse(string(values, "occurredAt")),
                string(values, "idempotencyKey"),
                string(values, "fingerprint"),
                provenance(values)
        );
    }

    private StateObservation mapObservation(Record record) {
        Map<String, Object> values = record.get("observation").asMap();
        return new StateObservation(
                UUID.fromString(string(values, "id")),
                UUID.fromString(string(values, "entityId")),
                DynamicPayload.toMap(string(values, "payloadJson"), objectMapper),
                string(values, "origin"),
                number(values, "confidence").doubleValue(),
                Instant.parse(string(values, "observedAt")),
                provenance(values)
        );
    }

    private EntitySpecification mapSpecification(Record record) {
        Map<String, Object> values = record.get("spec").asMap();
        return new EntitySpecification(
                UUID.fromString(string(values, "entityId")),
                number(values, "version").longValue(),
                DynamicPayload.toMap(string(values, "payloadJson"), objectMapper),
                Instant.parse(string(values, "updatedAt")),
                provenance(values)
        );
    }

    private static Map<String, Object> transitionParameters(
            StateTransitionCommand command,
            String payloadJson,
            String fingerprint,
            UUID transitionId
    ) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("contextId", command.contextId());
        parameters.put("ownerSubject", command.ownerSubject());
        parameters.put("entityId", command.entityId().toString());
        parameters.put("expectedVersion", command.expectedVersion());
        parameters.put("toVersion", command.expectedVersion() + 1);
        parameters.put("payloadJson", payloadJson);
        parameters.put("origin", command.origin());
        parameters.put("confidence", command.confidence());
        parameters.put("reason", command.reason());
        parameters.put("occurredAt", command.occurredAt().toString());
        parameters.put("idempotencyKey", command.idempotencyKey());
        parameters.put("fingerprint", fingerprint);
        parameters.put("transitionId", transitionId.toString());
        putProvenance(parameters, command.provenance());
        return parameters;
    }

    private static void putProvenance(Map<String, Object> parameters, StateProvenance provenance) {
        StateProvenance safe = provenance == null ? StateProvenance.system() : provenance;
        parameters.put("sourceType", safe.sourceType());
        parameters.put("sourceId", safe.sourceId());
        parameters.put("sourceVersion", safe.sourceVersion());
    }

    private static String fingerprint(StateTransitionCommand command, String payloadJson) {
        StateProvenance provenance = command.provenance();
        return DynamicPayload.fingerprint(
                command.expectedVersion(), payloadJson, command.origin(), command.confidence(),
                command.reason(), command.idempotencyKey(), provenance.sourceType(),
                provenance.sourceId(), provenance.sourceVersion()
        );
    }

    private static UUID transitionId(UUID entityId, String idempotencyKey) {
        return UUID.nameUUIDFromBytes((entityId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    }

    private static TransitionResult throwIdempotencyConflict(String idempotencyKey) {
        throw new StateIdempotencyConflictException(idempotencyKey);
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Propriedade ausente no estado dinâmico: " + key);
        }
        return value.toString();
    }

    private static String optionalString(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID optionalUuid(Map<String, Object> values, String key) {
        String value = optionalString(values, key);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static Number number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number;
        }
        return Double.parseDouble(value.toString());
    }

    private static StateProvenance provenance(Map<String, Object> values) {
        return new StateProvenance(
                optionalString(values, "sourceType"),
                optionalString(values, "sourceId"),
                optionalString(values, "sourceVersion")
        );
    }
}
