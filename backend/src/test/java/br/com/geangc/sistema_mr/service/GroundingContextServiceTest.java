package br.com.geangc.sistema_mr.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.geangc.sistema_mr.configuration.DocumentProperties;
import br.com.geangc.sistema_mr.model.ChatFile;
import br.com.geangc.sistema_mr.model.DocumentStatus;
import br.com.geangc.sistema_mr.repository.DocumentRepository;
import br.com.geangc.sistema_mr.repository.DocumentRepository.GroundingMatch;
import br.com.geangc.sistema_mr.storage.DocumentStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroundingContextServiceTest {

    @Test
    void includesOnlyExplicitAttachmentsWhenRelatedSearchIsDisabled() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID id = UUID.randomUUID();
        ChatFile file = readyFile(id, "relatório.pdf", "explicit-context", 20);

        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner")).thenReturn(List.of(file));
        when(storage.readText("explicit-context")).thenReturn("CONTEÚDO COMPLETO DO ARQUIVO");

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Resuma", List.of(id), false);

        assertTrue(prepared.modelPrompt().contains("CONTEÚDO COMPLETO DO ARQUIVO"));
        assertTrue(prepared.modelPrompt().contains("Resuma"));
        assertEquals(1, prepared.files().size());
        assertTrue(prepared.files().getFirst().explicitlyAttached());
        verify(repository, never()).hasReadyFiles("chat-owner", "owner");
        verifyNoInteractions(embeddings);
    }

    @Test
    void combinesExplicitAndSemanticAttachmentsWhenRelatedSearchIsEnabled() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID explicitId = UUID.randomUUID();
        UUID relatedId = UUID.randomUUID();
        ChatFile explicit = readyFile(explicitId, "contrato.pdf", "explicit-context", 20);
        ChatFile related = readyFile(relatedId, "aditivo.pdf", "related-context", 20);
        List<Float> embedding = List.of(.1f, .2f);

        when(repository.findReadyOwnedByIds(List.of(explicitId), "chat-owner", "owner"))
                .thenReturn(List.of(explicit));
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Compare")).thenReturn(embedding);
        when(repository.searchReadyFiles("chat-owner", "owner", embedding))
                .thenReturn(List.of(new GroundingMatch(related, .91)));
        when(storage.readText("explicit-context")).thenReturn("CONTRATO");
        when(storage.readText("related-context")).thenReturn("ADITIVO");

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Compare", List.of(explicitId), true);

        assertEquals(2, prepared.files().size());
        assertTrue(prepared.files().get(0).explicitlyAttached());
        assertFalse(prepared.files().get(1).explicitlyAttached());
        assertTrue(prepared.modelPrompt().contains("CONTRATO"));
        assertTrue(prepared.modelPrompt().contains("ADITIVO"));
    }

    @Test
    void usesSemanticSearchWhenNoAttachmentsAreSelected() throws Exception {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID relatedId = UUID.randomUUID();
        ChatFile related = readyFile(relatedId, "manual.pdf", "related-context", 20);
        List<Float> embedding = List.of(.3f, .4f);

        when(repository.findReadyOwnedByIds(List.of(), "chat-owner", "owner")).thenReturn(List.of());
        when(repository.hasReadyFiles("chat-owner", "owner")).thenReturn(true);
        when(embeddings.embedQuery("Como configurar?")).thenReturn(embedding);
        when(repository.searchReadyFiles("chat-owner", "owner", embedding))
                .thenReturn(List.of(new GroundingMatch(related, .84)));
        when(storage.readText("related-context")).thenReturn("MANUAL");

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));
        var prepared = service.prepare("chat-owner", "owner", "Como configurar?", List.of(), false);

        assertEquals(1, prepared.files().size());
        assertFalse(prepared.files().getFirst().explicitlyAttached());
        assertTrue(prepared.modelPrompt().contains("MANUAL"));
    }

    @Test
    void rejectsAttachmentsThatAreMissingNotOwnedOrNotReady() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentEmbeddingService embeddings = mock(DocumentEmbeddingService.class);
        UUID id = UUID.randomUUID();
        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner")).thenReturn(List.of());

        var service = new GroundingContextService(repository, storage, embeddings, properties(200_000));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.prepare("chat-owner", "owner", "Resuma", List.of(id), false));

        assertTrue(exception.getMessage().contains("não existem"));
        verifyNoInteractions(storage, embeddings);
    }

    @Test
    void rejectsExplicitAttachmentsThatExceedTheContextBudget() {
        DocumentRepository repository = mock(DocumentRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findReadyOwnedByIds(List.of(id), "chat-owner", "owner"))
                .thenReturn(List.of(readyFile(id, "relatório.pdf", "context-key", 101)));

        var service = new GroundingContextService(
                repository, mock(DocumentStorage.class), mock(DocumentEmbeddingService.class), properties(100)
        );

        assertThrows(GroundingContextLimitException.class,
                () -> service.prepare("chat-owner", "owner", "Pergunta", List.of(id), false));
    }

    private static ChatFile readyFile(UUID id, String name, String contextStorageKey, int tokenCount) {
        Instant now = Instant.now();
        return new ChatFile(
                id, "chat-owner", "owner", name, "application/pdf", 10, "hash",
                id + "/original", contextStorageKey, DocumentStatus.READY, null, tokenCount,
                "gemini-embedding-2", now, now
        );
    }

    private static DocumentProperties properties(int budget) {
        return new DocumentProperties(
                Path.of("data/files"), 10, 20 * 1024 * 1024, 800, 1000, 20,
                "gemini-embedding-2", 768, 100, 3, .6, budget,
                new DocumentProperties.Ocr("http://127.0.0.1:8082", 120, 12, .55)
        );
    }
}
