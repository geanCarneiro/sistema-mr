package br.com.geangc.sistema_mr.agent.state.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.agent.state.repository.DynamicStateRepository;
import br.com.geangc.sistema_mr.agent.state.model.DynamicEntity;
import br.com.geangc.sistema_mr.agent.state.model.EntityState;
import br.com.geangc.sistema_mr.agent.state.model.StateObservation;
import br.com.geangc.sistema_mr.agent.state.model.StateProjection;
import br.com.geangc.sistema_mr.agent.state.model.StateTransitionCommand;
import br.com.geangc.sistema_mr.agent.state.model.TransitionResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DynamicStateServiceTest {

    private final DynamicStateRepository repository = mock(DynamicStateRepository.class);
    private final DynamicStateService service = new DynamicStateService(repository);

    @Test
    void delegatesStateTransitionAndKeepsIdempotentResult() {
        StateTransitionCommand command = command();
        TransitionResult expected = new TransitionResult(null, true);
        when(repository.applyStateTransition(command)).thenReturn(expected);

        assertEquals(expected, service.transition(command));
        verify(repository).applyStateTransition(command);
    }

    @Test
    void normalizesProjectionPathsBeforeReading() {
        UUID entityId = UUID.randomUUID();
        StateProjection expected = new StateProjection(2, Map.of("/name", "Consulta"));
        when(repository.projectCurrentState(entityId, "owner", "chat-owner", List.of("/name")))
                .thenReturn(expected);

        assertEquals(expected, service.projectState(entityId, "owner", "chat-owner", Set.of("/name")));
        verify(repository).projectCurrentState(entityId, "owner", "chat-owner", List.of("/name"));
    }

    @Test
    void rejectsObservationWithInvalidConfidenceBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.observe(
                UUID.randomUUID(), UUID.randomUUID(), "owner", "chat-owner", Map.of(),
                "MODEL", 1.1, Instant.now(), null
        ));
    }

    private static StateTransitionCommand command() {
        return new StateTransitionCommand(
                UUID.randomUUID(), "owner", "chat-owner", 0,
                Map.of("name", "Consulta"), "MODEL", 0.8,
                "Extraído do documento", "run-1-step-1", Instant.parse("2026-09-06T18:00:00Z"), null
        );
    }
}
