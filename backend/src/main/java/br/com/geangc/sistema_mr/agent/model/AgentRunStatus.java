package br.com.geangc.sistema_mr.agent.model;

public enum AgentRunStatus {
    SCHEDULED,
    RUNNING,
    WAITING_FOR_USER,
    WAITING_FOR_TOOL,
    WAITING_FOR_CAPACITY,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean active() {
        return this == RUNNING || this == WAITING_FOR_USER || this == WAITING_FOR_TOOL;
    }

    public boolean resumable() {
        return this == SCHEDULED || this == WAITING_FOR_TOOL || this == WAITING_FOR_CAPACITY;
    }
}
