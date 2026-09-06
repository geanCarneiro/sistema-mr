package br.com.geangc.sistema_mr.state.model;

public record Provenance(
        String sourceType,
        String sourceId,
        String sourceVersion,
        String locator
) {
    public Provenance {
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("O tipo da fonte é obrigatório");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("O identificador da fonte é obrigatório");
        }
    }
}
