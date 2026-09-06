package br.com.geangc.sistema_mr.agent.state.service;

import br.com.geangc.sistema_mr.agent.state.repository.DynamicStateRepository;
import br.com.geangc.sistema_mr.agent.state.model.DynamicEntity;
import br.com.geangc.sistema_mr.agent.state.model.EntitySpecification;
import br.com.geangc.sistema_mr.agent.state.model.EntityState;
import br.com.geangc.sistema_mr.agent.state.model.StateObservation;
import br.com.geangc.sistema_mr.agent.state.model.StateProvenance;
import br.com.geangc.sistema_mr.agent.state.model.StateProjection;
import br.com.geangc.sistema_mr.agent.state.model.StateTransitionCommand;
import br.com.geangc.sistema_mr.agent.state.model.TransitionResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DynamicStateService {

    private final DynamicStateRepository repository;

    public DynamicStateService(DynamicStateRepository repository) {
        this.repository = repository;
    }

    public DynamicEntity createEntity(String ownerSubject, String contextId, String type) {
        return createEntity(UUID.randomUUID(), ownerSubject, contextId, type, Instant.now());
    }

    public DynamicEntity createEntity(
            UUID entityId,
            String ownerSubject,
            String contextId,
            String type,
            Instant createdAt
    ) {
        return repository.createEntity(new DynamicEntity(entityId, ownerSubject, contextId, type, createdAt));
    }

    public TransitionResult transition(StateTransitionCommand command) {
        return repository.applyStateTransition(command);
    }

    public EntityState currentState(UUID entityId, String ownerSubject, String contextId) {
        return repository.findCurrentState(entityId, ownerSubject, contextId)
                .orElseThrow(() -> new IllegalArgumentException("Entidade dinâmica não encontrada"));
    }

    public StateProjection projectState(
            UUID entityId,
            String ownerSubject,
            String contextId,
            Set<String> paths
    ) {
        List<String> normalizedPaths = br.com.geangc.sistema_mr.agent.state.DynamicPayload.normalizePaths(paths);
        return repository.projectCurrentState(entityId, ownerSubject, contextId, normalizedPaths);
    }

    public StateObservation observe(
            UUID observationId,
            UUID entityId,
            String ownerSubject,
            String contextId,
            Map<String, Object> payload,
            String origin,
            double confidence,
            Instant observedAt,
            StateProvenance provenance
    ) {
        if (origin == null || origin.isBlank() || confidence < 0 || confidence > 1 || Double.isNaN(confidence)) {
            throw new IllegalArgumentException("A observação exige origem e confiança entre 0 e 1");
        }
        return repository.recordObservation(
                new StateObservation(observationId, entityId, payload, origin, confidence, observedAt, provenance),
                contextId,
                ownerSubject
        );
    }

    public EntitySpecification specification(
            UUID entityId,
            String ownerSubject,
            String contextId,
            long expectedVersion,
            Map<String, Object> payload,
            StateProvenance provenance,
            Instant updatedAt
    ) {
        return repository.saveSpecification(
                entityId, ownerSubject, contextId, expectedVersion, payload, provenance, updatedAt
        );
    }
}
