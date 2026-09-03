package br.com.geangc.sistema_mr.controller.dto;

import br.com.geangc.sistema_mr.model.InteractionSource;
import br.com.geangc.sistema_mr.service.GroundingContextService;
import java.util.UUID;

public record GroundingFileDto(
        UUID id,
        String name,
        String sourceType,
        Double similarity,
        boolean available
) {

    public static GroundingFileDto from(GroundingContextService.GroundingFile file) {
        return new GroundingFileDto(
                file.id(),
                file.name(),
                file.explicitlyAttached() ? "EXPLICIT" : "SEMANTIC",
                file.similarity(),
                true
        );
    }

    public static GroundingFileDto from(InteractionSource source) {
        return new GroundingFileDto(
                source.fileId(),
                source.name(),
                source.sourceType().name(),
                source.similarity(),
                source.available()
        );
    }
}
