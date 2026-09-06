package br.com.geangc.sistema_mr.state.service;

import br.com.geangc.sistema_mr.state.model.CurrentState;
import br.com.geangc.sistema_mr.state.model.CurrentStateProjection;
import br.com.geangc.sistema_mr.state.model.DynamicEntity;
import br.com.geangc.sistema_mr.state.model.Observation;
import br.com.geangc.sistema_mr.state.model.Specification;
import br.com.geangc.sistema_mr.state.model.StateChangeMetadata;
import br.com.geangc.sistema_mr.state.model.StatePatch;
import br.com.geangc.sistema_mr.state.model.StateTransition;
import br.com.geangc.sistema_mr.state.repository.DynamicStateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DynamicStateService {

    private final DynamicStateRepository repository;
    private final ObjectMapper objectMapper;
    private final FlexiblePayloadCodec payloadCodec;

    public DynamicStateService(DynamicStateRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, new FlexiblePayloadCodec(objectMapper));
    }

    public DynamicStateService(
            DynamicStateRepository repository,
            ObjectMapper objectMapper,
            FlexiblePayloadCodec payloadCodec
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.payloadCodec = payloadCodec;
    }

    public DynamicEntity createEntity(
            String ownerSubject,
            String contextId,
            String type,
            Map<String, Object> initialPayload,
            StateChangeMetadata metadata
    ) {
        validateScope(ownerSubject, contextId);
        require(type, "O tipo da entidade é obrigatório");
        require(metadata, "Os metadados da alteração são obrigatórios");
        if (initialPayload == null) {
            initialPayload = Map.of();
        }
        Instant now = metadata.occurredAt();
        return repository.create(
                new DynamicEntity(UUID.randomUUID(), ownerSubject, contextId, type, now, now),
                DynamicPayloadOperations.copyMap(initialPayload),
                metadata
        );
    }

    public DynamicEntity createEntity(
            String ownerSubject,
            String contextId,
            String type,
            String payloadDocument,
            StateChangeMetadata metadata
    ) {
        return createEntity(ownerSubject, contextId, type, payloadCodec.parse(payloadDocument), metadata);
    }

    public Observation recordObservation(
            UUID entityId,
            String ownerSubject,
            String contextId,
            String type,
            Map<String, Object> payload,
            StateChangeMetadata metadata
    ) {
        validateScope(ownerSubject, contextId);
        require(entityId, "O identificador da entidade é obrigatório");
        require(type, "O tipo da observação é obrigatório");
        require(metadata, "Os metadados da observação são obrigatórios");
        return repository.saveObservation(new Observation(
                UUID.randomUUID(),
                entityId,
                ownerSubject,
                contextId,
                type,
                DynamicPayloadOperations.copyMap(payload),
                metadata.origin(),
                metadata.confidence(),
                metadata.provenance(),
                metadata.occurredAt()
        ));
    }

    public Observation recordObservation(
            UUID entityId,
            String ownerSubject,
            String contextId,
            String type,
            String payloadDocument,
            StateChangeMetadata metadata
    ) {
        return recordObservation(
                entityId,
                ownerSubject,
                contextId,
                type,
                payloadCodec.parse(payloadDocument),
                metadata
        );
    }

    public CurrentState updateState(
            UUID entityId,
            String ownerSubject,
            String contextId,
            int expectedVersion,
            StatePatch patch,
            StateChangeMetadata metadata
    ) {
        validateScope(ownerSubject, contextId);
        require(entityId, "O identificador da entidade é obrigatório");
        require(patch, "O patch do estado é obrigatório");
        require(metadata, "Os metadados da alteração são obrigatórios");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("A versão esperada deve ser positiva");
        }
        CurrentState current = current(entityId, ownerSubject, contextId);
        Map<String, Object> after = DynamicPayloadOperations.apply(
                current.payload(), patch.set(), patch.remove());
        String changesJson = "{\"set\":" + toJsonWithConfiguredMapper(patch.set())
                + ",\"remove\":" + toJsonWithConfiguredMapper(patch.remove()) + "}";
        DynamicStateRepository.StateChangeResult result = repository.applyPatch(
                entityId,
                ownerSubject,
                contextId,
                expectedVersion,
                toJsonWithConfiguredMapper(current.payload()),
                toJsonWithConfiguredMapper(after),
                changesJson,
                metadata
        );
        if ("CONFLICT".equals(result.outcome())) {
            throw new StateVersionConflictException(expectedVersion, result.currentState().version());
        }
        return result.currentState();
    }

    public CurrentStateProjection currentProjection(
            UUID entityId,
            String ownerSubject,
            String contextId,
            Set<String> paths
    ) {
        validateScope(ownerSubject, contextId);
        require(entityId, "O identificador da entidade é obrigatório");
        CurrentStateProjection current = repository.findCurrentProjection(entityId, ownerSubject, contextId)
                .orElseThrow(() -> new IllegalArgumentException("A entidade não pertence ao usuário"));
        return new CurrentStateProjection(
                current.entityId(),
                current.type(),
                current.version(),
                DynamicPayloadOperations.project(current.payload(), paths),
                current.provenance(),
                current.confidence(),
                current.updatedAt()
        );
    }

    public Specification saveSpecification(
            UUID entityId,
            String ownerSubject,
            String contextId,
            int version,
            Map<String, Object> payload,
            StateChangeMetadata metadata
    ) {
        validateScope(ownerSubject, contextId);
        require(entityId, "O identificador da entidade é obrigatório");
        require(metadata, "Os metadados da especificação são obrigatórios");
        if (version < 1) {
            throw new IllegalArgumentException("A versão da especificação deve ser positiva");
        }
        return repository.saveSpecification(new Specification(
                UUID.randomUUID(),
                entityId,
                ownerSubject,
                contextId,
                version,
                DynamicPayloadOperations.copyMap(payload),
                metadata.provenance(),
                metadata.occurredAt()
        ));
    }

    public Specification saveSpecification(
            UUID entityId,
            String ownerSubject,
            String contextId,
            int version,
            String payloadDocument,
            StateChangeMetadata metadata
    ) {
        return saveSpecification(
                entityId,
                ownerSubject,
                contextId,
                version,
                payloadCodec.parse(payloadDocument),
                metadata
        );
    }

    public List<StateTransition> transitions(UUID entityId, String ownerSubject, String contextId) {
        validateScope(ownerSubject, contextId);
        require(entityId, "O identificador da entidade é obrigatório");
        return repository.findTransitions(entityId, ownerSubject, contextId);
    }

    public List<Observation> observations(UUID entityId, String ownerSubject, String contextId) {
        validateScope(ownerSubject, contextId);
        require(entityId, "O identificador da entidade é obrigatório");
        return repository.findObservations(entityId, ownerSubject, contextId);
    }

    public java.util.Optional<Specification> latestSpecification(
            UUID entityId,
            String ownerSubject,
            String contextId
    ) {
        validateScope(ownerSubject, contextId);
        require(entityId, "O identificador da entidade é obrigatório");
        return repository.findLatestSpecification(entityId, ownerSubject, contextId);
    }

    private CurrentState current(UUID entityId, String ownerSubject, String contextId) {
        return repository.findCurrent(entityId, ownerSubject, contextId)
                .orElseThrow(() -> new IllegalArgumentException("A entidade não pertence ao usuário"));
    }

    private String toJsonWithConfiguredMapper(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Payload dinâmico não serializável", exception);
        }
    }

    private static void validateScope(String ownerSubject, String contextId) {
        require(ownerSubject, "O usuário é obrigatório");
        require(contextId, "O contexto é obrigatório");
    }

    private static void require(Object value, String message) {
        if (value == null || (value instanceof String string && string.isBlank())) {
            throw new IllegalArgumentException(message);
        }
    }
}
