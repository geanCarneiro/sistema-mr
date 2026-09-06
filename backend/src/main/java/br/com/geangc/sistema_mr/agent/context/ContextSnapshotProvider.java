package br.com.geangc.sistema_mr.agent.context;

import java.util.UUID;

@FunctionalInterface
public interface ContextSnapshotProvider {
    ContextSnapshot snapshot(UUID subjectId, String conversationId, String ownerSubject, String prompt);

    static ContextSnapshotProvider empty() {
        return (subjectId, conversationId, ownerSubject, prompt) -> new ContextSnapshot(
                subjectId, conversationId, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
}
