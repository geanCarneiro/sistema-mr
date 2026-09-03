package br.com.geangc.sistema_mr.service;

import br.com.geangc.sistema_mr.model.Interaction;
import br.com.geangc.sistema_mr.model.InteractionSource;
import br.com.geangc.sistema_mr.repository.InteractionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InteractionService {

    private final InteractionRepository repository;

    public InteractionService(InteractionRepository repository) {
        this.repository = repository;
    }

    public void persistCompleted(
            UUID interactionId,
            UUID userMessageId,
            UUID assistantMessageId,
            String prompt,
            String response,
            String conversationId,
            String ownerSubject,
            Instant createdAt,
            Instant completedAt,
            List<GroundingContextService.GroundingFile> groundingFiles
    ) {
        List<InteractionSource> sources = groundingFiles.stream()
                .map(file -> new InteractionSource(
                        file.id(),
                        file.name(),
                        file.explicitlyAttached()
                                ? InteractionSource.SourceType.EXPLICIT
                                : InteractionSource.SourceType.SEMANTIC,
                        file.similarity(),
                        true
                ))
                .toList();

        repository.saveCompleted(new Interaction(
                interactionId,
                userMessageId,
                assistantMessageId,
                prompt,
                response,
                conversationId,
                ownerSubject,
                createdAt,
                completedAt,
                sources
        ));
    }

    public List<Interaction> findHistory(String conversationId, String ownerSubject) {
        return repository.findOwned(conversationId, ownerSubject);
    }
}
