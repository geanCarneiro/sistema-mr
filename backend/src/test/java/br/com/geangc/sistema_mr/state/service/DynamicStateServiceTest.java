package br.com.geangc.sistema_mr.state.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.state.model.CurrentState;
import br.com.geangc.sistema_mr.state.model.CurrentStateProjection;
import br.com.geangc.sistema_mr.state.model.Provenance;
import br.com.geangc.sistema_mr.state.model.StateChangeMetadata;
import br.com.geangc.sistema_mr.state.model.StateChangeOrigin;
import br.com.geangc.sistema_mr.state.model.StatePatch;
import br.com.geangc.sistema_mr.state.repository.DynamicStateRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DynamicStateServiceTest {

    private static final UUID ENTITY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-06T18:00:00Z");
    private DynamicStateRepository repository;
    private DynamicStateService service;

    @BeforeEach
    void setUp() {
        repository = mock(DynamicStateRepository.class);
        service = new DynamicStateService(repository, new ObjectMapper());
    }

    @Test
    void updatesDynamicPayloadAndCarriesProvenanceToRepository() throws Exception {
        CurrentState current = current(3, Map.of("name", "Conta", "active", true));
        when(repository.findCurrent(ENTITY_ID, "owner", "chat-owner")).thenReturn(Optional.of(current));
        CurrentState updated = current(4, Map.of("name", "Conta atualizada", "category", "finance"));
        when(repository.applyPatch(
                eq(ENTITY_ID), eq("owner"), eq("chat-owner"), eq(3), any(), any(), any(), any()))
                .thenReturn(new DynamicStateRepository.StateChangeResult("APPLIED", updated));
        StateChangeMetadata metadata = new StateChangeMetadata(
                StateChangeOrigin.DOCUMENT,
                0.91,
                "Extração confirmada",
                NOW,
                "document-1:version-2",
                List.of(new Provenance("Arquivo", "file-1", "2", "page:1"))
        );

        CurrentState result = service.updateState(
                ENTITY_ID,
                "owner",
                "chat-owner",
                3,
                new StatePatch(
                        Map.of("name", "Conta atualizada", "category", "finance"),
                        Set.of("active")
                ),
                metadata
        );

        assertEquals(updated, result);
        var invocation = org.mockito.Mockito.mockingDetails(repository).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("applyPatch"))
                .findFirst()
                .orElseThrow();
        assertEquals("owner", invocation.getArgument(1));
        assertEquals("chat-owner", invocation.getArgument(2));
        assertEquals(Integer.valueOf(3), invocation.getArgument(3));
        assertTrue(((String) invocation.getArgument(4)).contains("\"name\":\"Conta\""));
        assertTrue(((String) invocation.getArgument(5)).contains("\"category\":\"finance\""));
        assertEquals("document-1:version-2", ((StateChangeMetadata) invocation.getArgument(7)).idempotencyKey());
    }

    @Test
    void returnsCurrentStateForDuplicateWithoutTreatingItAsConflict() {
        CurrentState current = current(2, Map.of("status", "paid"));
        when(repository.findCurrent(ENTITY_ID, "owner", "chat-owner")).thenReturn(Optional.of(current));
        when(repository.applyPatch(
                eq(ENTITY_ID), eq("owner"), eq("chat-owner"), eq(2), any(), any(), any(), any()))
                .thenReturn(new DynamicStateRepository.StateChangeResult("DUPLICATE", current));

        CurrentState result = service.updateState(
                ENTITY_ID,
                "owner",
                "chat-owner",
                2,
                new StatePatch(Map.of("status", "paid"), Set.of()),
                StateChangeMetadata.of(StateChangeOrigin.AGENT, 1.0, "Repetição segura", "run-1:step-1")
        );

        assertEquals(current, result);
    }

    @Test
    void raisesVersionConflictAndKeepsPersistenceAtomic() {
        CurrentState current = current(5, Map.of("status", "paid"));
        when(repository.findCurrent(ENTITY_ID, "owner", "chat-owner")).thenReturn(Optional.of(current));
        when(repository.applyPatch(
                eq(ENTITY_ID), eq("owner"), eq("chat-owner"), eq(4), any(), any(), any(), any()))
                .thenReturn(new DynamicStateRepository.StateChangeResult("CONFLICT", current));

        assertThrows(StateVersionConflictException.class, () -> service.updateState(
                ENTITY_ID,
                "owner",
                "chat-owner",
                4,
                new StatePatch(Map.of("status", "cancelled"), Set.of()),
                StateChangeMetadata.of(StateChangeOrigin.USER, 1.0, "Correção manual", "user:1")
        ));
        verify(repository).applyPatch(eq(ENTITY_ID), eq("owner"), eq("chat-owner"), eq(4), any(), any(), any(), any());
    }

    @Test
    void projectsOnlyRequestedPathsAndDoesNotReadHistory() {
        CurrentStateProjection current = new CurrentStateProjection(
                ENTITY_ID,
                "invoice",
                2,
                new LinkedHashMap<>(Map.of(
                        "number", "123",
                        "customer", Map.of("name", "Ana", "email", "ana@example.com")
                )),
                List.of(new Provenance("Arquivo", "file-1", null, "page:1")),
                0.95,
                NOW
        );
        when(repository.findCurrentProjection(ENTITY_ID, "owner", "chat-owner"))
                .thenReturn(Optional.of(current));

        CurrentStateProjection result = service.currentProjection(
                ENTITY_ID, "owner", "chat-owner", Set.of("customer.name"));

        assertEquals(Map.of("name", "Ana"), result.payload().get("customer"));
        assertEquals(2, result.version());
        verify(repository, never()).findTransitions(any(), any(), any());
    }

    @Test
    void requiresUserAndContextScope() {
        assertThrows(IllegalArgumentException.class, () -> service.currentProjection(
                ENTITY_ID, "", "chat-owner", Set.of("status")));
        assertThrows(IllegalArgumentException.class, () -> service.currentProjection(
                ENTITY_ID, "owner", null, Set.of("status")));
        verify(repository, never()).findCurrentProjection(any(), any(), any());
    }

    private static CurrentState current(int version, Map<String, Object> payload) {
        return new CurrentState(ENTITY_ID, version, payload, List.of(), 1.0, NOW);
    }
}
