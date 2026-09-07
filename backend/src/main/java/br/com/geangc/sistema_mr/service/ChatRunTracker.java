package br.com.geangc.sistema_mr.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class ChatRunTracker {

    private final ConcurrentHashMap<UUID, TrackedRun> runs = new ConcurrentHashMap<>();

    public void start(UUID runId, String ownerSubject, String message) {
        runs.put(runId, new TrackedRun(ownerSubject, new RunSnapshot(
                runId, 0, "RUNNING", "RECEIVED", message, null, null, false, Instant.now())));
    }

    public void update(UUID runId, String ownerSubject, String phase, String message) {
        update(runId, ownerSubject, "RUNNING", phase, message);
    }

    public void update(UUID runId, String ownerSubject, String status, String phase, String message) {
        TrackedRun run = owned(runId, ownerSubject);
        synchronized (run) {
            RunSnapshot current = run.snapshot;
            run.snapshot = new RunSnapshot(
                    runId, current.revision() + 1, status, phase, message,
                    null, null, false, Instant.now());
            signal(run);
        }
    }

    public void complete(
            UUID runId,
            String ownerSubject,
            ChatApplicationService.ChatResult result,
            String phase,
            String message
    ) {
        TrackedRun run = owned(runId, ownerSubject);
        synchronized (run) {
            run.snapshot = new RunSnapshot(
                    runId, run.snapshot.revision() + 1, result.status().name(), phase,
                    message, result, null, result.status().terminal(), result.timestamp());
            signal(run);
        }
    }

    public void fail(UUID runId, String ownerSubject, String message) {
        TrackedRun run = owned(runId, ownerSubject);
        synchronized (run) {
            run.snapshot = new RunSnapshot(
                    runId, run.snapshot.revision() + 1, "FAILED", "FAILED", message,
                    null, message, true, Instant.now());
            signal(run);
        }
    }

    public RunSnapshot await(UUID runId, String ownerSubject, long afterRevision, Duration timeout) {
        TrackedRun run = owned(runId, ownerSubject);
        CompletableFuture<Void> signal;
        synchronized (run) {
            if (run.snapshot.revision() > afterRevision || run.snapshot.terminal()) {
                return run.snapshot;
            }
            signal = run.signal;
        }
        try {
            signal.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ignored) {
            // O timeout é um retorno válido do long polling; o snapshot atual acompanha a resposta.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível aguardar a execução", exception);
        }
        synchronized (run) {
            return run.snapshot;
        }
    }

    public RunSnapshot current(UUID runId, String ownerSubject) {
        return owned(runId, ownerSubject).snapshot;
    }

    private TrackedRun owned(UUID runId, String ownerSubject) {
        TrackedRun run = runs.get(runId);
        if (run == null || !run.ownerSubject.equals(ownerSubject)) {
            throw new IllegalArgumentException("A execução não pertence ao usuário");
        }
        return run;
    }

    private static void signal(TrackedRun run) {
        run.signal.complete(null);
        run.signal = new CompletableFuture<>();
    }

    private static final class TrackedRun {
        private final String ownerSubject;
        private RunSnapshot snapshot;
        private CompletableFuture<Void> signal = new CompletableFuture<>();

        private TrackedRun(String ownerSubject, RunSnapshot snapshot) {
            this.ownerSubject = ownerSubject;
            this.snapshot = snapshot;
        }
    }

    public record RunSnapshot(
            UUID runId,
            long revision,
            String status,
            String phase,
            String message,
            ChatApplicationService.ChatResult result,
            String failureReason,
            boolean terminal,
            Instant timestamp
    ) {}
}
