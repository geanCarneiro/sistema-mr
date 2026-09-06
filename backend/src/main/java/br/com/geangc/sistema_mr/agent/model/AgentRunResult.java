package br.com.geangc.sistema_mr.agent.model;

import java.time.Instant;
import java.util.UUID;

public record AgentRunResult(
        UUID runId,
        AgentRunStatus status,
        String content,
        Instant completedAt,
        String providerId,
        String modelId,
        String failureReason
) {
    public boolean completed() {
        return status == AgentRunStatus.COMPLETED;
    }
}
