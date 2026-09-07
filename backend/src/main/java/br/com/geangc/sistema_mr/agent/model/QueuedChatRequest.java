package br.com.geangc.sistema_mr.agent.model;

import java.util.List;
import java.util.UUID;

public record QueuedChatRequest(
        UUID runId,
        String prompt,
        List<UUID> attachmentIds,
        boolean includeRelatedFiles,
        UUID subjectId,
        UUID privacyReviewFileId
) {
    public QueuedChatRequest {
        if (runId == null || prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Uma execução agendada precisa de runId e prompt");
        }
        attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
    }

    public QueuedChatRequest(
            UUID runId,
            String prompt,
            List<UUID> attachmentIds,
            boolean includeRelatedFiles,
            UUID subjectId
    ) {
        this(runId, prompt, attachmentIds, includeRelatedFiles, subjectId, null);
    }
}
