package br.com.geangc.sistema_mr.agent.state;

public class StateVersionConflictException extends RuntimeException {
    private final long expectedVersion;
    private final long currentVersion;

    public StateVersionConflictException(long expectedVersion, long currentVersion) {
        super("Conflito de versão do estado: esperada " + expectedVersion + ", atual " + currentVersion);
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
