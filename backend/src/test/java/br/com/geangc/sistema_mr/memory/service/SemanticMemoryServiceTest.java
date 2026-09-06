package br.com.geangc.sistema_mr.memory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.memory.model.MemoryOrigin;
import br.com.geangc.sistema_mr.memory.model.MemoryRetentionPolicy;
import br.com.geangc.sistema_mr.memory.model.MemoryStatus;
import br.com.geangc.sistema_mr.memory.model.SemanticMemory;
import br.com.geangc.sistema_mr.memory.repository.SemanticMemoryRepository;
import br.com.geangc.sistema_mr.state.model.Provenance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SemanticMemoryServiceTest {

    private SemanticMemoryRepository repository;
    private SemanticMemoryService service;
    private final Provenance provenance = new Provenance("USER_MESSAGE", "message-1", "1", "text");

    @BeforeEach
    void setUp() {
        repository = mock(SemanticMemoryRepository.class);
        service = new SemanticMemoryService(repository);
    }

    @Test
    void createsDeclaredMemoryWithRetentionAndProvenance() {
        when(repository.create(any(SemanticMemory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SemanticMemory memory = service.remember(
                "owner", "chat-owner", "subject-1", "timezone", "America/Sao_Paulo",
                "USER_DECLARED", 1.0, null, "UNTIL_REVOKED", provenance);

        assertEquals(MemoryOrigin.USER_DECLARED, memory.origin());
        assertEquals(MemoryRetentionPolicy.UNTIL_REVOKED, memory.retentionPolicy());
        assertEquals(MemoryStatus.ACTIVE, memory.status());
        assertEquals(List.of(provenance), memory.provenance());
        verify(repository).create(any(SemanticMemory.class));
    }

    @Test
    void queriesOnlyAValidatedBoundedSetOfRelevantTerms() {
        when(repository.findRelevant(eq("owner"), eq("chat-owner"), eq(List.of("prefere", "café")), eq(6), any()))
                .thenReturn(List.of());

        service.findRelevant("owner", "chat-owner", "Prefere café e café", 6);

        verify(repository).findRelevant(eq("owner"), eq("chat-owner"), eq(List.of("prefere", "café")), eq(6), any());
    }

    @Test
    void replacesUsingExpectedVersionAndKeepsReplacementLink() {
        UUID oldId = UUID.randomUUID();
        SemanticMemory replacement = memory(UUID.randomUUID(), MemoryStatus.ACTIVE, 1, oldId);
        when(repository.replace(eq(oldId), eq("owner"), eq("chat-owner"), eq(3), any(SemanticMemory.class)))
                .thenReturn(Optional.of(replacement));

        SemanticMemory result = service.update(
                oldId, "owner", "chat-owner", 3, "timezone", "UTC", "MODEL_INFERRED", 0.7,
                "2026-12-31T00:00:00Z", "UNTIL_VALID_UNTIL", provenance);

        assertEquals(replacement, result);
        verify(repository).replace(eq(oldId), eq("owner"), eq("chat-owner"), eq(3), any(SemanticMemory.class));
    }

    @Test
    void rejectsExpiringPolicyWithoutExpiration() {
        assertThrows(IllegalArgumentException.class, () -> service.remember(
                "owner", "chat-owner", null, "key", "value", "MODEL_INFERRED", 0.8,
                null, "UNTIL_VALID_UNTIL", provenance));
    }

    @Test
    void reportsConcurrentDeleteInsteadOfClaimingSuccess() {
        UUID memoryId = UUID.randomUUID();
        when(repository.delete(eq(memoryId), eq("owner"), eq("chat-owner"), eq(2), any())).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.delete(memoryId, "owner", "chat-owner", 2));
    }

    private static SemanticMemory memory(UUID id, MemoryStatus status, int version, UUID supersedes) {
        Instant now = Instant.parse("2026-09-06T18:00:00Z");
        return new SemanticMemory(id, "owner", "chat-owner", null, "key", "value",
                MemoryOrigin.MODEL_INFERRED, 0.8,
                List.of(new Provenance("RUN", "run-1", null, null)), now, null,
                MemoryRetentionPolicy.UNTIL_REVOKED, status, version, supersedes, null, now, now, null);
    }
}
