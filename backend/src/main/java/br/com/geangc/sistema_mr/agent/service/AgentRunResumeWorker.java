package br.com.geangc.sistema_mr.agent.service;

import br.com.geangc.sistema_mr.agent.model.AgentRun;
import br.com.geangc.sistema_mr.agent.model.AgentRunStatus;
import br.com.geangc.sistema_mr.agent.model.QueuedChatRequest;
import br.com.geangc.sistema_mr.agent.repository.AgentRunRepository;
import br.com.geangc.sistema_mr.configuration.AgentRuntimeSchedulerProperties;
import br.com.geangc.sistema_mr.service.ChatApplicationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Consome a fila local e reconstitui solicitações de chat após restart. */
@Component
public class AgentRunResumeWorker {

    private static final Logger LOGGER = Logger.getLogger(AgentRunResumeWorker.class.getName());
    private final InMemoryRunScheduler scheduler;
    private final AgentRunRepository repository;
    private final ChatApplicationService chatApplicationService;
    private final Executor executor;
    private final long retryDelayMillis;
    private volatile boolean running = true;

    public AgentRunResumeWorker(
            InMemoryRunScheduler scheduler,
            AgentRunRepository repository,
            ChatApplicationService chatApplicationService,
            @Qualifier("agentResumeTaskExecutor") Executor executor,
            AgentRuntimeSchedulerProperties properties
    ) {
        this.scheduler = scheduler;
        this.repository = repository;
        this.chatApplicationService = chatApplicationService;
        this.executor = executor;
        this.retryDelayMillis = properties.retryDelayMillis();
    }

    @PostConstruct
    void start() {
        executor.execute(this::runLoop);
    }

    @PreDestroy
    void stop() {
        running = false;
    }

    private void runLoop() {
        while (running) {
            try {
                UUIDHolder next = new UUIDHolder(scheduler.take());
                process(next.value());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Worker de retomada não conseguiu processar a fila", exception);
                pauseBeforeRetry();
            }
        }
    }

    private void process(java.util.UUID runId) {
        Optional<QueuedChatRequest> request = scheduler.load(runId);
        Optional<AgentRun> run = repository.findById(runId);
        if (request.isEmpty() || run.isEmpty()) {
            LOGGER.warning("Execução " + runId + " não possui snapshot suficiente para retomada; removida da fila");
            scheduler.complete(runId);
            return;
        }
        if (run.get().terminal()) {
            scheduler.complete(runId);
            return;
        }
        try {
            chatApplicationService.resume(run.get(), request.get());
            scheduler.complete(runId);
        } catch (AgentRunUnavailableException exception) {
            scheduler.enqueue(runId);
            pauseBeforeRetry();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Execução " + runId + " falhou durante a retomada", exception);
            scheduler.enqueue(runId);
            pauseBeforeRetry();
        }
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private record UUIDHolder(java.util.UUID value) {}
}
