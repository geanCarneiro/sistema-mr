package br.com.geangc.sistema_mr.controller.dto;

import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

public record ChatFileDto(
        UUID id,
        String name,
        String mimeType,
        long size,
        DocumentStatus status,
        String errorMessage,
        int contextTokenCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChatFileDto from(ChatFile file) {
        return new ChatFileDto(
                file.id(), file.originalName(), file.mimeType(), file.size(), file.status(),
                file.errorMessage(), file.contextTokenCount(), file.createdAt(), file.updatedAt()
        );
    }
}
