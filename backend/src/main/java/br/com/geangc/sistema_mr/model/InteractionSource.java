package br.com.geangc.sistema_mr.model;

import java.util.UUID;

public record InteractionSource(
        UUID fileId,
        String name,
        SourceType sourceType,
        Double similarity,
        boolean available
) {

    public enum SourceType {
        EXPLICIT,
        SEMANTIC
    }
}
