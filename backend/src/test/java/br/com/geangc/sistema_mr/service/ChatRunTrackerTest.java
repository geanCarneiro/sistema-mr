package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRunTrackerTest {

    @Test
    void returnsTheCurrentSnapshotAfterAStateChange() {
        ChatRunTracker tracker = new ChatRunTracker();
        UUID runId = UUID.randomUUID();
        tracker.start(runId, "owner", "Recebi sua mensagem.");

        tracker.update(runId, "owner", "LOCAL_VISION", "Estou interpretando a imagem localmente…");

        ChatRunTracker.RunSnapshot snapshot = tracker.await(runId, "owner", 0, Duration.ofMillis(10));

        assertEquals(1, snapshot.revision());
        assertEquals("LOCAL_VISION", snapshot.phase());
        assertFalse(snapshot.terminal());
    }

    @Test
    void timeoutStillReturnsTheLastKnownSnapshot() {
        ChatRunTracker tracker = new ChatRunTracker();
        UUID runId = UUID.randomUUID();
        tracker.start(runId, "owner", "Recebi sua mensagem.");

        ChatRunTracker.RunSnapshot snapshot = tracker.await(runId, "owner", 0, Duration.ofMillis(5));

        assertEquals(0, snapshot.revision());
        assertEquals("RUNNING", snapshot.status());
    }

    @Test
    void doesNotExposeAnotherOwnersRun() {
        ChatRunTracker tracker = new ChatRunTracker();
        UUID runId = UUID.randomUUID();
        tracker.start(runId, "owner", "Recebi sua mensagem.");

        assertThrows(IllegalArgumentException.class,
                () -> tracker.current(runId, "other-owner"));
    }
}
