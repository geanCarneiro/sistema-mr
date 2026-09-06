package br.com.geangc.sistema_mr.agent.state.model;

public record StateProvenance(
        String sourceType,
        String sourceId,
        String sourceVersion
) {
    public StateProvenance {
        sourceType = sourceType == null || sourceType.isBlank() ? "SYSTEM" : sourceType;
        sourceId = sourceId == null ? "" : sourceId;
        sourceVersion = sourceVersion == null ? "" : sourceVersion;
    }

    public static StateProvenance system() {
        return new StateProvenance("SYSTEM", "", "");
    }
}
