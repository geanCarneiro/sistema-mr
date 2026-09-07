package br.com.geangc.sistema_mr.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.agent.model.QueuedChatRequest;
import br.com.geangc.sistema_mr.agent.repository.AgentRunRepository;
import br.com.geangc.sistema_mr.configuration.AgentRuntimeSchedulerProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InMemoryRunSchedulerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresQueuedRequestFromLocalFilesAfterReconstruction() throws Exception {
        AgentRunRepository repository = mock(AgentRunRepository.class);
        when(repository.findResumable()).thenReturn(List.of());
        AgentRuntimeSchedulerProperties properties = new AgentRuntimeSchedulerProperties(
                temporaryDirectory.toString(), 1_000);
        UUID runId = UUID.randomUUID();
        QueuedChatRequest request = new QueuedChatRequest(
                runId, "Use apenas dados fictícios", List.of(UUID.randomUUID()), true, UUID.randomUUID());

        InMemoryRunScheduler first = new InMemoryRunScheduler(repository, properties);
        first.register(request);
        first.enqueue(runId);

        InMemoryRunScheduler restarted = new InMemoryRunScheduler(repository, properties);
        restarted.restorePendingRuns();

        assertEquals(runId, restarted.take());
        assertEquals(request, restarted.load(runId).orElseThrow());
        String snapshot = Files.readString(temporaryDirectory.resolve(runId + ".properties"));
        assertFalse(snapshot.contains("ownerSubject"));
        assertTrue(Files.exists(temporaryDirectory.resolve("pending-runs.txt")));
    }

    @Test
    void completeRemovesSnapshotAndQueueEntry() throws Exception {
        AgentRunRepository repository = mock(AgentRunRepository.class);
        when(repository.findResumable()).thenReturn(List.of());
        InMemoryRunScheduler scheduler = new InMemoryRunScheduler(
                repository, new AgentRuntimeSchedulerProperties(temporaryDirectory.toString(), 1_000));
        UUID runId = UUID.randomUUID();

        scheduler.register(new QueuedChatRequest(runId, "teste", List.of(), false, null));
        scheduler.enqueue(runId);
        scheduler.complete(runId);

        assertEquals(0, scheduler.pendingCount());
        assertTrue(scheduler.load(runId).isEmpty());
        assertFalse(Files.exists(temporaryDirectory.resolve(runId + ".properties")));
    }
}
