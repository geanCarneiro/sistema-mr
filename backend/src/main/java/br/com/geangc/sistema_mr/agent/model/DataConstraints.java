package br.com.geangc.sistema_mr.agent.model;

public record DataConstraints(
        String mode,
        String purpose
) {
    public static DataConstraints unspecified() {
        return new DataConstraints("UNSPECIFIED", "agent-run");
    }

    public boolean localOnly() {
        return "LOCAL_ONLY".equals(mode);
    }
}
