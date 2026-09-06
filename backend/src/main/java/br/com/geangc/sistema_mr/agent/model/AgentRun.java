package br.com.geangc.sistema_mr.agent.model;

import java.time.Instant;
import java.util.UUID;

public record AgentRun(
        UUID id,
        UUID subjectId,
        String conversationId,
        String ownerSubject,
        AgentRunTrigger trigger,
        AgentRunStatus status,
        int version,
        int steps,
        int modelInvocations,
        int toolCalls,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String result,
        String failureReason
) {
    public boolean terminal() {
        return status.terminal();
    }
}
