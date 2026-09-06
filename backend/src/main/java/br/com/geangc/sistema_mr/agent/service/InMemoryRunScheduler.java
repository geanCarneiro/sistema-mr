package br.com.geangc.sistema_mr.agent.service;

import br.com.geangc.sistema_mr.agent.model.AgentRun;
import br.com.geangc.sistema_mr.agent.repository.AgentRunRepository;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRunScheduler {

    private final AgentRunRepository repository;
    private final BlockingQueue<UUID> queue = new LinkedBlockingQueue<>();

    public InMemoryRunScheduler(AgentRunRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void restorePendingRuns() {
        repository.findResumable().stream()
                .map(AgentRun::id)
                .forEach(queue::offer);
    }

    public void enqueue(UUID runId) {
        queue.offer(runId);
    }

    public int pendingCount() {
        return queue.size();
    }
}
