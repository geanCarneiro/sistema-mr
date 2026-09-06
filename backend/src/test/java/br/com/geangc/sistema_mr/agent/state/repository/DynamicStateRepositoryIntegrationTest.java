package br.com.geangc.sistema_mr.agent.state.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.geangc.sistema_mr.agent.state.StateVersionConflictException;
import br.com.geangc.sistema_mr.agent.state.StateIdempotencyConflictException;
import br.com.geangc.sistema_mr.agent.state.model.DynamicEntity;
import br.com.geangc.sistema_mr.agent.state.model.EntityState;
import br.com.geangc.sistema_mr.agent.state.model.StateProvenance;
import br.com.geangc.sistema_mr.agent.state.model.StateTransition;
import br.com.geangc.sistema_mr.agent.state.model.StateTransitionCommand;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "NEO4J_TEST_URI", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DynamicStateRepositoryIntegrationTest {

    private final Driver driver = GraphDatabase.driver(
            System.getenv("NEO4J_TEST_URI"),
            AuthTokens.basic(
                    System.getenv().getOrDefault("NEO4J_TEST_USER", "neo4j"),
                    System.getenv().getOrDefault("NEO4J_TEST_PASSWORD", "password")
            )
    );
    private final DynamicStateRepository repository = new DynamicStateRepository(driver, new ObjectMapper());

    @BeforeEach
    void initialize() {
        repository.initializeSchema();
    }

    @Test
    void persistsDynamicStateWithHistoryProjectionIdempotencyAndConflictHandling() throws Exception {
        String owner = "integration-" + UUID.randomUUID();
        String contextId = "chat-" + owner;
        UUID entityId = UUID.randomUUID();
        Instant firstAt = Instant.parse("2026-09-06T18:00:00Z");
        String sourceId = createContext(contextId, owner);
        DynamicEntity entity = repository.createEntity(
                new DynamicEntity(entityId, owner, contextId, "consulta", firstAt)
        );

        StateTransitionCommand first = command(entity.id(), owner, contextId, 0,
                Map.of("name", "Consulta", "obsolete", true), "step-1", firstAt,
                new StateProvenance("DOCUMENT", sourceId, "v1"));
        assertFalse(repository.applyStateTransition(first).duplicate());
        assertTrue(repository.applyStateTransition(first).duplicate());
        assertThrows(StateIdempotencyConflictException.class, () -> repository.applyStateTransition(
                command(entity.id(), owner, contextId, 0, Map.of("name", "outra alteração"),
                        "step-1", firstAt, new StateProvenance("DOCUMENT", sourceId, "v1"))
        ));

        StateTransitionCommand second = command(entity.id(), owner, contextId, 1,
                Map.of("name", "Retorno"), "step-2", firstAt.plusSeconds(1),
                new StateProvenance("DOCUMENT", sourceId, "v2"));
        repository.applyStateTransition(second);

        EntityState current = repository.findCurrentState(entityId, owner, contextId).orElseThrow();
        assertEquals(2, current.version());
        assertEquals("Retorno", current.payload().get("name"));
        assertFalse(current.payload().containsKey("obsolete"));
        assertEquals(1, repository.projectCurrentState(entityId, owner, contextId, List.of("/name"))
                .values().size());
        assertFalse(repository.projectCurrentState(entityId, owner, contextId, List.of("/name"))
                .values().containsKey("/obsolete"));
        assertThrows(StateVersionConflictException.class, () -> repository.applyStateTransition(
                command(entity.id(), owner, contextId, 0, Map.of("name", "conflito"), "step-3", firstAt,
                        StateProvenance.system())
        ));

        List<StateTransition> history = repository.findTransitionHistory(entityId, owner, contextId);
        assertEquals(2, history.size());
        assertTrue(history.get(1).previousPayload().containsKey("obsolete"));
        assertEquals(sourceId, history.get(1).provenance().sourceId());
        assertEquals(2, countDocumentProvenance(entityId, owner, contextId, sourceId));

        UUID concurrentEntityId = UUID.randomUUID();
        repository.createEntity(new DynamicEntity(
                concurrentEntityId, owner, contextId, "concorrente", firstAt
        ));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> left = executor.submit(() -> succeedsOrConflicts(command(
                    concurrentEntityId, owner, contextId, 0, Map.of("winner", "left"),
                    "concurrent-left", firstAt, StateProvenance.system()
            )));
            Future<Boolean> right = executor.submit(() -> succeedsOrConflicts(command(
                    concurrentEntityId, owner, contextId, 0, Map.of("winner", "right"),
                    "concurrent-right", firstAt, StateProvenance.system()
            )));
            boolean leftResult = left.get();
            boolean rightResult = right.get();
            long concurrentVersion = repository.findCurrentState(concurrentEntityId, owner, contextId)
                    .orElseThrow().version();
            int concurrentHistory = repository.findTransitionHistory(concurrentEntityId, owner, contextId).size();
            assertEquals(1, (leftResult ? 1 : 0) + (rightResult ? 1 : 0),
                    "left=" + leftResult + ", right=" + rightResult
                            + ", version=" + concurrentVersion + ", history=" + concurrentHistory);
        } finally {
            executor.shutdownNow();
        }
    }

    @AfterAll
    void closeDriver() {
        driver.close();
    }

    private String createContext(String contextId, String owner) {
        String sourceId = UUID.randomUUID().toString();
        try (var session = driver.session()) {
            session.run("CREATE (:ContextoChat {id: $id, ownerSubject: $owner}) "
                            + "CREATE (:Arquivo {id: $sourceId, conversationId: $id, ownerSubject: $owner})",
                    Map.of("id", contextId, "owner", owner, "sourceId", sourceId)).consume();
        }
        return sourceId;
    }

    private long countDocumentProvenance(UUID entityId, String owner, String contextId, String sourceId) {
        try (var session = driver.session()) {
            return session.run("MATCH (transition:EstadoTransicao {entityId: $entityId, "
                            + "ownerSubject: $owner, contextId: $contextId, sourceId: $sourceId})"
                            + "-[:DERIVADO_DE]->(:Arquivo) RETURN count(transition) AS total",
                    Map.of("entityId", entityId.toString(), "owner", owner,
                            "contextId", contextId, "sourceId", sourceId))
                    .single().get("total").asLong();
        }
    }

    private boolean succeedsOrConflicts(StateTransitionCommand command) {
        try {
            repository.applyStateTransition(command);
            return true;
        } catch (StateVersionConflictException exception) {
            return false;
        }
    }

    private static StateTransitionCommand command(
            UUID entityId,
            String owner,
            String contextId,
            long version,
            Map<String, Object> payload,
            String idempotencyKey,
            Instant occurredAt,
            StateProvenance provenance
    ) {
        return new StateTransitionCommand(
                entityId, owner, contextId, version, payload,
                "MODEL", 0.9, "Atualização de integração", idempotencyKey, occurredAt, provenance
        );
    }
}
